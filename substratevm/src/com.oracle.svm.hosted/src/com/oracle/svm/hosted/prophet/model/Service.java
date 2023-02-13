package com.oracle.svm.hosted.prophet.model;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Service {

    private Name serviceName;

    private Set<Field> serviceFields = new HashSet<>();

    private Set<Method> serviceMethods = new HashSet<>();

    public Service(Name name) {
        this.serviceName = new Name(name);
    }

    public void setServiceFields(Set<Field> serviceFields) {
        this.serviceFields = serviceFields;
    }

    public void setServiceMethods(Set<Method> serviceMethods) {
        this.serviceMethods = serviceMethods;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Service service = (Service) o;
        return Objects.equals(serviceName, service.serviceName) && Objects.equals(serviceFields, service.serviceFields) && Objects.equals(serviceMethods, service.serviceMethods);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceName, serviceFields, serviceMethods);
    }

    @Override
    public String toString() {
        return "Service{" +
                "\nserviceName=" + serviceName +
                "\nserviceFields=" + serviceFields +
                "\nserviceMethods=" + serviceMethods +
                '}';
    }
}
