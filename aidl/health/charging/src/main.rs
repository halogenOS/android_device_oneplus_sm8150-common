// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! Charge limit HAL of the sm8150 phones. It limits the battery to the target that the system
//! publishes. Above the target, the phone runs on battery while the charger is plugged in with
//! its input suspended. At the target, the charger powers the phone without charging. Below the
//! target, the charger charges the battery.

#![forbid(unsafe_code)]
#![deny(clippy::all, clippy::pedantic, unused_results, clippy::let_underscore_must_use)]

mod battery;
mod charger;
mod controller;
mod error;
mod flow;
mod power_supply;
mod service;
mod sysfs;
mod termination;

use std::process::ExitCode;

use android_logger::Config;
use binder::ProcessState;
use log::{error, info, LevelFilter};
use tokio::runtime;

use crate::{
    charger::Charger, controller::Controller, error::Error, flow::Flows,
    power_supply::PowerSupplyEvents, termination::Termination,
};

const LOG_TAG: &str = "charge_limit";

fn main() -> ExitCode {
    android_logger::init_once(
        Config::default().with_tag(LOG_TAG).with_max_level(LevelFilter::Info),
    );
    match run() {
        Ok(()) => ExitCode::SUCCESS,
        Err(error) => {
            error!("{}", error.describe());
            ExitCode::FAILURE
        }
    }
}

fn run() -> Result<(), Error> {
    // Clean exits and SIGTERM drop the charger, which restores normal charging. After a panic,
    // which aborts the process in device builds, or after SIGKILL, init restarts the HAL, and the
    // first apply sets normal charging.
    let charger = Charger::new();
    let runtime =
        runtime::Builder::new_current_thread().enable_io().build().map_err(Error::StartRuntime)?;
    runtime.block_on(async {
        let termination = Termination::watch()?;
        let power_supply = PowerSupplyEvents::open()?;
        let (flows, attachments, targets) = Flows::new();
        service::register(attachments)?;
        ProcessState::start_thread_pool();
        tokio::select! {
            result = Controller::new(charger, targets).run(power_supply) => {
                result.map(|never| match never {})
            }
            () = flows.run() => Ok(()),
            received = termination.received() => {
                received.inspect(|()| info!("Received SIGTERM, charging normally"))
            }
        }
    })
}
