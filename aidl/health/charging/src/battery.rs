// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! The level and the current of the battery, read from the battery driver.

use std::ops::RangeInclusive;

use crate::{error::Error, sysfs::Attribute};

/// The level the system shows to the user, from the battery driver.
const CAPACITY: Attribute = Attribute::new("/sys/class/power_supply/battery/capacity");

/// The current that the fuel gauge measures through the battery, positive while the battery
/// discharges (`oplus_battery_get_property` and `bq27541_get_average_current` of the oplus charger
/// driver).
const CURRENT_NOW: Attribute = Attribute::new("/sys/class/power_supply/battery/current_now");

const PERCENT: RangeInclusive<i32> = 0..=100;

/// A battery level in percent.
#[derive(Clone, Copy, Debug, PartialEq, Eq, PartialOrd, Ord)]
pub struct BatteryLevel(u8);

impl BatteryLevel {
    /// Reads the level the battery has now.
    pub fn read() -> Result<Self, Error> {
        let value = CAPACITY.read()?;
        Self::within(PERCENT, value)
            .ok_or(Error::UnexpectedValue { attribute: CAPACITY.path(), value })
    }

    /// The level of `percent`, if `levels` contains it.
    pub fn within(levels: RangeInclusive<i32>, percent: i32) -> Option<Self> {
        u8::try_from(percent).ok().filter(|_| levels.contains(&percent)).map(Self)
    }
}

/// A reading of the battery current.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct BatteryCurrent(i32);

impl BatteryCurrent {
    /// Reads the latest current of the fuel gauge.
    pub fn read() -> Result<Self, Error> {
        CURRENT_NOW.read().map(Self)
    }

    /// Whether the battery powers the phone.
    pub fn discharges(self) -> bool {
        self.0 > 0
    }
}
