// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! Charge limit HAL of the sm8150 phones. Above the target, the phone runs on battery while the
//! charger is plugged in with its input suspended. At the target, the charger powers the phone
//! without charging. Below the target, the charger charges the battery.

#![forbid(unsafe_code)]
#![deny(clippy::all, clippy::pedantic, unused_results, clippy::let_underscore_must_use)]

mod charger;

use std::process::ExitCode;

use crate::charger::Sm8150Charger;

fn main() -> ExitCode {
    charge_limit_hal::serve(Sm8150Charger::new())
}
