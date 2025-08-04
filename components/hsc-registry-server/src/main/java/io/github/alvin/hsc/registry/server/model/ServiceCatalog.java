package io.github.alvin.hsc.registry.server.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Service Catalog Model
 * 服务目录模型
 * 
 * @author Alvin
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServiceCatalog {

    private Map<String, List<ServiceInstance>> services;
    private int totalServices;
    private int totalInstances;

    // Constructors
    public ServiceCatalog() {}

    public ServiceCatalog(Map<String, List<ServiceInstance>> services) {
        this.services = services;
        this.totalServices = services.size();
        this.totalInstances = services.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    // Getters and Setters
    public Map<String, List<ServiceInstance>> getServices() {
        return services;
    }

    public void setServices(Map<String, List<ServiceInstance>> services) {
        this.services = services;
        this.totalServices = services != null ? services.size() : 0;
        this.totalInstances = services != null ? services.values().stream()
                .mapToInt(List::size)
                .sum() : 0;
    }

    public int getTotalServices() {
        return totalServices;
    }

    public void setTotalServices(int totalServices) {
        this.totalServices = totalServices;
    }

    public int getTotalInstances() {
        return totalInstances;
    }

    public void setTotalInstances(int totalInstances) {
        this.totalInstances = totalInstances;
    }

    @Override
    public String toString() {
        return String.format("ServiceCatalog{totalServices=%d, totalInstances=%d}", 
                totalServices, totalInstances);
    }
}