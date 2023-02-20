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

    public Service(Name name, Set<Field> serviceFields, Set<Method> serviceMethods) {
        this.serviceName = new Name(name);
        this.serviceFields = serviceFields;
        this.serviceMethods = serviceMethods;
    }

    // public Service clone() {
    //     Set<Field> newFields = new HashSet<>(this.serviceFields.size());
    //     this.getServiceFields().forEach(x -> {
    //         newFields.add(x.clone());
    //     });

    //     // Set<Method> newServiceMethod = new HashSet<>(this.serviceMethods.size());
    //     // this.getServiceMethods().forEach(y -> {
    //     //     newServiceMethod.add(y.clone());
    //     // });

    //     return new Service(new Name(this.getServiceName()), newFields);
    // }

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
        return "\nLayer Type: {Service}" +
                "\nService Name: {" + serviceName + "}"+
                "\nFields: {" + serviceFields + "}" +
                "\nMethods" + serviceMethods +"}"+
                '\n';
    }

    public Name getServiceName() {
        return serviceName;
    }

    public Set<Field> getServiceFields() {
        return serviceFields;
    }

    public Set<Method> getServiceMethods() {
        return serviceMethods;
    }
}
