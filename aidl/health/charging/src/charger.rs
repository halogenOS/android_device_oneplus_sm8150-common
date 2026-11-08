// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The charger of the sm8150 phones: the oplus charging switch and the input suspend of the
//! qpnp-smb5 battery supply.

use charge_limit_hal::{
    sysfs::{Attribute, Switch},
    ChargeMode, Charger, Error,
};

/// The current that the fuel gauge measures through the battery, positive while the battery
/// discharges (`oplus_battery_get_property` and `bq27541_get_average_current` of the oplus charger
/// driver).
const CURRENT_NOW: Attribute = Attribute::new("/sys/class/power_supply/battery/current_now");

pub struct Sm8150Charger {
    /// Writing 1 resets the fast charging of the driver, so the switch writes only a change.
    charging_enabled: Switch,
    input: InputSuspend,
}

impl Sm8150Charger {
    pub const fn new() -> Self {
        Self {
            charging_enabled: Switch::new("/sys/class/oplus_chg/battery/mmi_charging_enable"),
            input: InputSuspend {
                vote: Switch::new("/sys/class/power_supply/battery/input_suspend"),
                current_at_write: None,
            },
        }
    }
}

impl Charger for Sm8150Charger {
    /// Sets the charging switch and then the input, also when the charging switch fails, and
    /// returns the first error.
    fn set(&mut self, mode: ChargeMode) -> Result<(), Error> {
        // The charging switch has its new value before the input resumes, so leaving Drain for
        // Hold never charges in between. A failed charging switch never leaves the input
        // suspended.
        let charging = self.charging_enabled.set(mode == ChargeMode::Charge);
        let input = match mode {
            ChargeMode::Drain => self.input.suspend(),
            ChargeMode::Charge | ChargeMode::Hold => self.input.resume(),
        };
        charging.and(input)
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
        // Writing the vote raises a power supply uevent, so the switch writes only a change.
        self.vote.set(false)
    }
}

/// A reading of the battery current.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
struct BatteryCurrent(i32);

impl BatteryCurrent {
    fn read() -> Result<Self, Error> {
        CURRENT_NOW.read().map(Self)
    }

    /// Whether the battery powers the phone.
    fn discharges(self) -> bool {
        self.0 > 0
    }
}
