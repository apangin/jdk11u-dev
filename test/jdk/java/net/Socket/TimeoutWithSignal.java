/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import sun.misc.Signal;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * @test
 * @bug 8312065
 * @summary Socket.connect does not timeout when profiling
 * @requires os.family == "linux"
 * @run main/timeout=5 TimeoutWithSignal
 */
public class TimeoutWithSignal {
    private static final Semaphore timedOut = new Semaphore(0);

    public static void main(String[] args) throws Exception {
        // Find OS thread ID of the current thread
        Path self = Paths.get("/proc/thread-self");
        if (!Files.exists(self)) {
            System.out.println("/proc/thread-self is not supported on this system");
            return;
        }
        String tid = Files.readSymbolicLink(self).getFileName().toString();

        // Setup SIGPROG handler
        Signal.handle(new Signal("PROF"), System.out::println);

        // Send SIGPROF to the current thread every 500 ms
        new Thread(() -> {
            try {
                do {
                    Runtime.getRuntime().exec("kill -SIGPROF " + tid).waitFor();
                } while (!timedOut.tryAcquire(500, TimeUnit.MILLISECONDS));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // SocketTimeoutException should normally happen in 1 second
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("10.0.0.0", 8080), 1000);
        } catch (SocketTimeoutException e) {
            timedOut.release();
        }
    }
}
