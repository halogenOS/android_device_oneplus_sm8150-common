// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The charger of the phone: the oplus charging switch and the input suspend of the qpnp-smb5
//! battery supply.

use std::cmp::Ordering;

use log::{error, info};

use crate::{
    battery::{BatteryCurrent, BatteryLevel},
    error::Error,
    sysfs::Attribute,
};

/// What the charger does with the power of a plugged-in charger.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ChargeMode {
    /// The charger charges the battery.
    Charge,
    /// The charger powers the phone without charging the battery.
    Hold,
    /// The phone runs on battery while the charger is plugged in.
    Drain,
}

impl ChargeMode {
    /// The mode that moves the battery from `level` to `target`.
    pub fn toward(target: BatteryLevel, level: BatteryLevel) -> Self {
        match level.cmp(&target) {
            Ordering::Less => Self::Charge,
            Ordering::Equal => Self::Hold,
            Ordering::Greater => Self::Drain,
        }
    }

    fn charging_enabled(self) -> bool {
        self == Self::Charge
    }

    fn input_suspended(self) -> bool {
        self == Self::Drain
    }
}

/// The charger. Dropping it restores normal charging.
pub struct Charger {
    charging_enabled: Switch,
    input: InputSuspend,
}

impl Charger {
    pub const fn new() -> Self {
        Self {
            charging_enabled: Switch(Attribute::new(
                "/sys/class/oplus_chg/battery/mmi_charging_enable",
            )),
            input: InputSuspend {
                vote: Switch(Attribute::new("/sys/class/power_supply/battery/input_suspend")),
                current_at_write: None,
            },
        }
    }

    /// Sets the charging switch and then the input, also when the charging switch fails, and
    /// returns the first error.
    pub fn set(&mut self, mode: ChargeMode) -> Result<(), Error> {
        // The charging switch has its new value before the input resumes, so leaving Drain for
        // Hold never charges in between. A failed charging switch never leaves the input
        // suspended.
        let charging = self.charging_enabled.set(mode.charging_enabled());
        let input = if mode.input_suspended() { self.input.suspend() } else { self.input.resume() };
        charging.and(input)
    }
}

impl Drop for Charger {
    fn drop(&mut self) {
        if let Err(error) = self.set(ChargeMode::Charge) {
            error!("Cannot return to normal charging: {}", error.describe());
        }
    }
}

/// The input suspend of the battery supply. The attribute reads the vote of the charger driver.
/// The oplus charger driver resumes the input behind that vote, so a suspended input is checked
/// against the battery current.
struct InputSuspend {
    vote: Switch,
    /// The battery current read when the vote was last written.
    current_at_write: Option<BatteryCurrent>,
}

impl InputSuspend {
    fn suspend(&mut self) -> Result<(), Error> {
        let current = BatteryCurrent::read()?;
        // The fuel gauge averages the current, and the driver returns its last reading while fast
        // charging blocks the gauge. A reading equal to the one at the last write can predate
        // that write, so the vote is written again only for a new reading.
        let charges = !current.discharges() && self.current_at_write != Some(current);
        if charges || !self.vote.read()? {
            // The driver ignores a vote equal to the one in place, so 0 comes before 1.
            self.vote.write(false)?;
            self.vote.write(true)?;
            self.current_at_write = Some(current);
        }
        Ok(())
    }

    fn resume(&self) -> Result<(), Error> {
        self.vote.set(false)
    }
}

/// A switch of a charger driver in sysfs: 1 is on, 0 is off.
struct Switch(Attribute);

impl Switch {
    fn set(&self, on: bool) -> Result<(), Error> {
        // Writing the input suspend raises a power supply uevent, and writing 1 to the charging
        // switch resets the fast charging of the driver, so only a change is written.
        if self.read()? != on {
            self.write(on)?;
        }
        Ok(())
    }

    fn write(&self, on: bool) -> Result<(), Error> {
        self.0.write(i32::from(on))?;
        info!("Set {} to {}", self.0.path(), i32::from(on));
        Ok(())
    }

    fn read(&self) -> Result<bool, Error> {
        match self.0.read()? {
            0 => Ok(false),
            1 => Ok(true),
            value => Err(Error::UnexpectedValue { attribute: self.0.path(), value }),
        }
    }
}
