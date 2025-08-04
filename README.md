### 1. 项目愿景与目标

本项目旨在通过从零开始构建一套核心的微服务框架，深入理解 Spring Cloud 各个组件的底层工作原理和设计思想。最终产出的框架将命名为 `handwritten-spring-cloud`，它将包含服务治理、配置管理、API 网关等微服务架构的核心功能。

**最终目标：**
* 掌握微服务架构的核心设计模式。
* 理解服务注册与发现、负载均衡、远程调用、配置中心、API 网关的实现细节。
* 产出一套可用于学习和演示的迷你微服务框架。

**技术栈基础：**
* **语言：** Java 21
* **核心框架：** Spring Boot 3.4.x
* **构建工具：** Gradle 8.10

**项目结构：**
```
handwritten-spring-cloud/
├── .gitignore                      # Git忽略文件
├── gradle.properties               # 全局属性，如版本号、依赖版本
├── gradlew                         # Gradle Wrapper
├── gradlew.bat                     # Gradle Wrapper (Windows)
├── gradle/                  
|   └── wrapper/                    # Gradle Wrapper 文件夹
│   │   └── ...
├── └── libs.versions.toml          # Version Catalog 定义
├── bom/
│   └── build.gradle                # (Bill of Materials) 统一的依赖版本管理模块
├── components/                     # 核心组件 (libraries) 存放目录
│   ├── hsc-core/                   # 框架核心，包含通用注解、工具类等
│   │   └── build.gradle            
│   ├── hsc-registry-server/        # 注册中心服务端
│   │   └── build.gradle            
│   ├── hsc-registry-client/        # 注册中心客户端
│   │   └── build.gradle            
│   ├── hsc-rpc/                    # 远程过程调用 (类似Feign + LoadBalancer)
│   │   └── build.gradle            
│   ├── hsc-config-server/          # 配置中心服务端
│   │   └── build.gradle            
│   ├── hsc-config-client/          # 配置中心客户端
│   │   └── build.gradle            
│   └── hsc-gateway/                # API 网关
│       └── build.gradle            
├── examples/                       # 使用手写框架的示例微服务
│   └── service-a/
│       └── build.gradle            
├── build.gradle                    # 根构建脚本，管理全局插件和配置
└── settings.gradle                 # 定义所有子模块 (libraries)
```