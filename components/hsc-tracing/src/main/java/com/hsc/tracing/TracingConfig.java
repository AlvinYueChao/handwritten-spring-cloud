package com.hsc.tracing;

import io.jaegertracing.Configuration;
import io.opentracing.Tracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * 分布式追踪配置类
 */
@org.springframework.context.annotation.Configuration
public class TracingConfig {

    @Value("${hsc.tracing.service-name:hsc-service}")
    private String serviceName;

    @Value("${hsc.tracing.jaeger.endpoint:http://localhost:14268/api/traces}")
    private String jaegerEndpoint;

    @Bean
    public Tracer jaegerTracer() {
        Configuration.SamplerConfiguration samplerConfig = Configuration.SamplerConfiguration.fromEnv()
                .withType("const")
                .withParam(1);

        Configuration.ReporterConfiguration reporterConfig = Configuration.ReporterConfiguration.fromEnv()
                .withLogSpans(true)
                .withSender(
                    Configuration.SenderConfiguration.fromEnv()
                        .withEndpoint(jaegerEndpoint)
                );

        Configuration config = new Configuration(serviceName)
                .withSampler(samplerConfig)
                .withReporter(reporterConfig);

        return config.getTracer();
    }

    @Bean
    public TraceContext traceContext() {
        return new TraceContext();
    }
}