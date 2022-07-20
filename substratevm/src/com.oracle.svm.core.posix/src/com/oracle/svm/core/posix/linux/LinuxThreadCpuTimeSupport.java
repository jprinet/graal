/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.posix.linux;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.oracle.svm.core.jdk.management.ThreadCpuTimeSupport;
import com.oracle.svm.core.posix.headers.Pthread.pthread_t;
import com.oracle.svm.core.posix.headers.linux.LinuxPthread;
import com.oracle.svm.core.posix.headers.linux.LinuxTime;
import com.oracle.svm.core.thread.VMThreads.OSThreadHandle;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.management.ManagementFeature;
import com.oracle.svm.core.posix.headers.Time.timespec;

final class LinuxThreadCpuTimeSupport implements ThreadCpuTimeSupport {

    @Override
    public long getCurrentThreadCpuTime(boolean includeSystemTime) {
        return getThreadCpuTimeImpl(LinuxTime.CLOCK_THREAD_CPUTIME_ID(), includeSystemTime);
    }

    /**
     * Returns the thread CPU time. Based on <link href=
     * "https://github.com/openjdk/jdk/blob/master/src/hotspot/os/linux/os_linux.cpp#L4956">fast_cpu_time</link>.
     *
     * @param osThreadHandle the pthread
     * @param includeSystemTime if {@code true} includes both system and user time, if {@code false}
     *            returns user time.
     */
    @Override
    public long getThreadCpuTime(OSThreadHandle osThreadHandle, boolean includeSystemTime) {
        CIntPointer threadsClockId = StackValue.get(Integer.BYTES);
        if (LinuxPthread.pthread_getcpuclockid((pthread_t) osThreadHandle, threadsClockId) != 0) {
            return -1;
        }
        return getThreadCpuTimeImpl(threadsClockId.read(), includeSystemTime);
    }

    private static long getThreadCpuTimeImpl(int clockId, boolean includeSystemTime) {
        if (!includeSystemTime) {
            throw new UnsupportedOperationException("ThreadCpuTime is supported only for combined user-mode and kernel-mode time.");
        }
        timespec time = StackValue.get(timespec.class);
        if (LinuxTime.clock_gettime(clockId, time) != 0) {
            return -1;
        }
        return TimeUnit.SECONDS.toNanos(time.tv_sec()) + time.tv_nsec();
    }
}

@Platforms({Platform.LINUX.class})
@AutomaticFeature
final class LinuxThreadCpuTimeFeature implements Feature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(ManagementFeature.class);
    }

    @Override
    public void afterRegistration(Feature.AfterRegistrationAccess access) {
        ImageSingletons.add(ThreadCpuTimeSupport.class, new LinuxThreadCpuTimeSupport());
    }
}
