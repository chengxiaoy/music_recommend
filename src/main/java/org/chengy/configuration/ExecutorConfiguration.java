package org.chengy.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Created by nali on 2017/9/28.
 */
@Configuration
public class ExecutorConfiguration {

    @Value("${crawler.user.threadnum}")
    private int threadNums;

    @Bean("songExecutor")
    public ThreadPoolTaskExecutor crawlSongExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("crawlsongthread--");
        //队列满的时候 生产者添加任务阻塞住
        executor.setRejectedExecutionHandler(new RejectExecutorforBlocking());
        executor.initialize();
        return executor;
    }
    @Bean("userExecutor")
    public ThreadPoolTaskExecutor crawlUserExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threadNums);
        executor.setMaxPoolSize(threadNums);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("crawluserthread--");
        //队列满的时候 生产者添加任务阻塞住
        executor.setRejectedExecutionHandler(new RejectExecutorforBlocking());
        executor.initialize();
        return executor;
    }

    class RejectExecutorforBlocking implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (!executor.isShutdown()) {
                try {
                    executor.getQueue().put(r);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }


}
