package top.aixmax.penetrate.common.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author wangxu
 * @version 1.0 2024/11/16 21:32
 * @description
 */
@Slf4j
public class ThreadFactoryBuilder {
    private String nameFormat = "thread-%d";
    private Boolean daemon = false;
    private Integer priority = Thread.NORM_PRIORITY;
    private Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
    private ThreadGroup threadGroup;

    public ThreadFactoryBuilder setNameFormat(String nameFormat) {
        this.nameFormat = nameFormat;
        return this;
    }

    public ThreadFactoryBuilder setDaemon(boolean daemon) {
        this.daemon = daemon;
        return this;
    }

    public ThreadFactoryBuilder setPriority(int priority) {
        this.priority = priority;
        return this;
    }

    public ThreadFactoryBuilder setUncaughtExceptionHandler(
            Thread.UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = uncaughtExceptionHandler;
        return this;
    }

    public ThreadFactoryBuilder setThreadGroup(ThreadGroup threadGroup) {
        this.threadGroup = threadGroup;
        return this;
    }

    public ThreadFactory build() {
        return new CustomThreadFactory(this);
    }

    private static class CustomThreadFactory implements ThreadFactory {
        private final String nameFormat;
        private final Boolean daemon;
        private final Integer priority;
        private final Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
        private final ThreadGroup threadGroup;
        private final AtomicInteger count = new AtomicInteger(1);

        private CustomThreadFactory(ThreadFactoryBuilder builder) {
            this.nameFormat = builder.nameFormat;
            this.daemon = builder.daemon;
            this.priority = builder.priority;
            this.uncaughtExceptionHandler = builder.uncaughtExceptionHandler != null ?
                    builder.uncaughtExceptionHandler :
                    (t, e) -> log.error("Uncaught exception in thread {}", t.getName(), e);
            this.threadGroup = builder.threadGroup != null ?
                    builder.threadGroup :
                    Thread.currentThread().getThreadGroup();
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(threadGroup, r,
                    String.format(nameFormat, count.getAndIncrement()));

            if (daemon != null) {
                thread.setDaemon(daemon);
            }

            if (priority != null) {
                thread.setPriority(priority);
            }

            thread.setUncaughtExceptionHandler(uncaughtExceptionHandler);

            return thread;
        }
    }
}
