// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! Applies the latest target to the battery each time the target or a power supply changes.

use std::convert::Infallible;

use log::error;
use tokio::sync::watch;

use crate::{
    battery::BatteryLevel,
    charger::{ChargeMode, Charger},
    error::Error,
    power_supply::PowerSupplyEvents,
};

/// Sets the charger from the target and the battery level.
pub struct Controller {
    charger: Charger,
    /// The latest target. `None` means normal charging.
    targets: watch::Receiver<Option<BatteryLevel>>,
}

impl Controller {
    pub fn new(charger: Charger, targets: watch::Receiver<Option<BatteryLevel>>) -> Self {
        Self { charger, targets }
    }

    /// Runs until the uevents stop.
    pub async fn run(mut self, power_supply: PowerSupplyEvents) -> Result<Infallible, Error> {
        loop {
            self.apply();
            tokio::select! {
                Ok(()) = self.targets.changed() => {}
                changed = power_supply.changed() => changed?,
            }
        }
    }

    fn apply(&mut self) {
        let target = *self.targets.borrow_and_update();
        let mode = target.map_or(ChargeMode::Charge, |target| {
            BatteryLevel::read().map_or_else(
                |error| {
                    // Hold neither drains the battery nor charges it past the target.
                    error!("Cannot read the battery level, holding it: {}", error.describe());
                    ChargeMode::Hold
                },
                |level| ChargeMode::toward(target, level),
            )
        });
        if let Err(error) = self.charger.set(mode) {
            // The next power supply uevent calls `apply` again.
            error!("Cannot set the charger to {mode:?}: {}", error.describe());
        }
    }
}
