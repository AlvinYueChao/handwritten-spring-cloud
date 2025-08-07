package io.github.alvin.hsc.registry.server.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Configuration Hot Reload Monitor
 * 配置热更新监控器，监控配置文件变化并触发重新加载
 * 
 * @author Alvin
 */
@Component
public class ConfigurationHotReloadMonitor {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationHotReloadMonitor.class);

    private final ApplicationEventPublisher eventPublisher;
    private final RegistryServerProperties registryServerProperties;
    private ScheduledExecutorService executorService;
    private WatchService watchService;

    public ConfigurationHotReloadMonitor(ApplicationEventPublisher eventPublisher,
                                       RegistryServerProperties registryServerProperties) {
        this.eventPublisher = eventPublisher;
        this.registryServerProperties = registryServerProperties;
    }

    /**
     * 应用启动完成后开始监控配置文件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void startConfigurationMonitoring() {
        // 检查是否启用配置热更新
        boolean hotReloadEnabled = Boolean.parseBoolean(
            System.getProperty("hsc.registry.config.hot-reload.enabled", "false"));
        
        if (!hotReloadEnabled) {
            logger.info("配置热更新功能未启用");
            return;
        }
        
        logger.info("启动配置热更新监控...");
        
        try {
            initializeWatchService();
            startPeriodicConfigCheck();
            logger.info("配置热更新监控已启动");
        } catch (Exception e) {
            logger.error("启动配置热更新监控失败", e);
        }
    }

    /**
     * 初始化文件监控服务
     */
    private void initializeWatchService() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        
        // 监控ConfigMap路径
        String configMapPath = System.getenv("CONFIG_MAP_PATH");
        if (configMapPath == null) {
            configMapPath = "/etc/config";
        }
        
        Path configPath = Paths.get(configMapPath);
        if (Files.exists(configPath)) {
            configPath.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
            logger.info("开始监控ConfigMap路径: {}", configMapPath);
        }
        
        // 监控Secret路径
        String secretPath = System.getenv("SECRET_PATH");
        if (secretPath == null) {
            secretPath = "/etc/secrets";
        }
        
        Path secretDir = Paths.get(secretPath);
        if (Files.exists(secretDir)) {
            secretDir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
            logger.info("开始监控Secret路径: {}", secretPath);
        }
        
        // 启动文件监控线程
        startFileWatchThread();
    }

    /**
     * 启动文件监控线程
     */
    private void startFileWatchThread() {
        Thread watchThread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key = watchService.take();
                    
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        
                        if (kind == StandardWatchEventKinds.OVERFLOW) {
                            continue;
                        }
                        
                        @SuppressWarnings("unchecked")
                        WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                        Path fileName = pathEvent.context();
                        
                        logger.info("检测到配置文件变化: {} - {}", fileName, kind.name());
                        
                        // 延迟处理，避免文件正在写入时读取
                        scheduleConfigurationReload(fileName.toString(), kind.name());
                    }
                    
                    boolean valid = key.reset();
                    if (!valid) {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("配置文件监控线程已停止");
            } catch (Exception e) {
                logger.error("配置文件监控出错", e);
            }
        });
        
        watchThread.setName("config-file-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    /**
     * 启动定期配置检查
     */
    private void startPeriodicConfigCheck() {
        executorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "config-reload-scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // 每30秒检查一次配置变化
        executorService.scheduleWithFixedDelay(
            this::checkConfigurationChanges, 
            30, 30, TimeUnit.SECONDS);
    }

    /**
     * 检查配置变化
     */
    private void checkConfigurationChanges() {
        try {
            logger.debug("执行定期配置检查...");
            
            // 这里可以添加配置文件哈希值比较等逻辑
            // 目前只是记录日志
            
        } catch (Exception e) {
            logger.warn("定期配置检查出错", e);
        }
    }

    /**
     * 调度配置重新加载
     */
    private void scheduleConfigurationReload(String fileName, String eventType) {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.schedule(() -> {
                try {
                    logger.info("开始重新加载配置，触发文件: {} ({})", fileName, eventType);
                    
                    // 发布配置变更事件
                    ConfigurationChangeEvent event = new ConfigurationChangeEvent(
                        this, fileName, eventType);
                    eventPublisher.publishEvent(event);
                    
                    logger.info("配置重新加载完成");
                } catch (Exception e) {
                    logger.error("配置重新加载失败", e);
                }
            }, 2, TimeUnit.SECONDS); // 延迟2秒执行
        }
    }

    /**
     * 停止监控
     */
    public void stopMonitoring() {
        logger.info("停止配置热更新监控...");
        
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.warn("关闭文件监控服务失败", e);
            }
        }
        
        logger.info("配置热更新监控已停止");
    }

    /**
     * 配置变更事件
     */
    public static class ConfigurationChangeEvent {
        private final Object source;
        private final String fileName;
        private final String eventType;
        private final long timestamp;

        public ConfigurationChangeEvent(Object source, String fileName, String eventType) {
            this.source = source;
            this.fileName = fileName;
            this.eventType = eventType;
            this.timestamp = System.currentTimeMillis();
        }

        public Object getSource() {
            return source;
        }

        public String getFileName() {
            return fileName;
        }

        public String getEventType() {
            return eventType;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format("ConfigurationChangeEvent{fileName='%s', eventType='%s', timestamp=%d}", 
                fileName, eventType, timestamp);
        }
    }
}