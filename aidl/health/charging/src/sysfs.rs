// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! Number attributes that kernel drivers expose in sysfs.

use std::fs;

use crate::error::Error;

/// A sysfs attribute that contains a decimal number.
pub struct Attribute {
    path: &'static str,
}

impl Attribute {
    pub const fn new(path: &'static str) -> Self {
        Self { path }
    }

    pub const fn path(&self) -> &'static str {
        self.path
    }

    pub fn read(&self) -> Result<i32, Error> {
        fs::read_to_string(self.path)
            .map_err(|error| Error::ReadAttribute(self.path, error))?
            .trim()
            .parse()
            .map_err(|error| Error::ParseAttribute(self.path, error))
    }

    pub fn write(&self, value: i32) -> Result<(), Error> {
        fs::write(self.path, value.to_string())
            .map_err(|error| Error::WriteAttribute(self.path, error))
    }
}
