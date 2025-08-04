# handwritten-spring-cloud

## 项目结构

```
handwritten-spring-cloud/
├── gradle/
│   └── libs.versions.toml          # Version Catalog 定义
├── bom/
│   └── build.gradle                # BOM 项目配置
├── components/
│   ├── hsc-core/
│   │   └── build.gradle            # 核心组件
│   ├── hsc-registry-server/
│   │   └── build.gradle            # 注册中心服务端
│   ├── hsc-registry-client/
│   │   └── build.gradle            # 注册中心客户端
│   ├── hsc-rpc/
│   │   └── build.gradle            # RPC 组件
│   ├── hsc-config-server/
│   │   └── build.gradle            # 配置中心服务端
│   ├── hsc-config-client/
│   │   └── build.gradle            # 配置中心客户端
│   └── hsc-gateway/
│       └── build.gradle            # API 网关
├── examples/
│   └── service-a/
│       └── build.gradle            # 示例服务
├── build.gradle                    # 根项目配置
└── settings.gradle                 # 项目设置
```