// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

//! SIGTERM, sent by init on `stop` and at shutdown.

use signal_hook::{consts::SIGTERM, low_level::pipe};
use tokio::{io::AsyncReadExt, net::UnixStream};

use crate::error::Error;

/// The read end of a socket pair that the signal handler of SIGTERM writes to.
pub struct Termination(UnixStream);

impl Termination {
    /// Registers the signal handler. Runs inside the Tokio runtime.
    pub fn watch() -> Result<Self, Error> {
        let (read, write) =
            std::os::unix::net::UnixStream::pair().map_err(Error::WatchTermination)?;
        // The handler is registered for the whole life of the process.
        let _signal_id = pipe::register(SIGTERM, write).map_err(Error::WatchTermination)?;
        read.set_nonblocking(true).map_err(Error::WatchTermination)?;
        UnixStream::from_std(read).map(Self).map_err(Error::WatchTermination)
    }

    /// Waits until SIGTERM arrives.
    pub async fn received(mut self) -> Result<(), Error> {
        self.0.read_exact(&mut [0]).await.map(|_length| ()).map_err(Error::ReceiveTermination)
    }
}
