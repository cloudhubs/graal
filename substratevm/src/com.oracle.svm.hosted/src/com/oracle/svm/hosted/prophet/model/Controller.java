package com.oracle.svm.hosted.prophet.model;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.lang.reflect.Method;

public class Controller{
    Set<Method> methods;
    Set<java.lang.reflect.Field> fields;
    Class<?> c;

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        
        builder.append("\n");
        builder.append("Layer Type: {Controller}");
        builder.append("\nClass: {" + c.getName() + "}");
        builder.append("\nMethods: {" + methods + "}");
        builder.append("\nFields: {" + fields + "}");
        builder.append("\n");

        return builder.toString();
    }

    public Controller(){
        methods = new HashSet<Method>();
        fields = new HashSet<java.lang.reflect.Field>();
        c = null;
    }

    // public void output(){
    //     System.out.println("Class: " + c.toString());
    //     for(java.lang.reflect.Field f : fields){
    //         System.out.println("Field: " + f.toString());
    //     }
    //     for(Method m : methods){
    //         System.out.println("Method: " + m.toString());
    //     }
    //     System.out.println("-----");
    // }

    public void addMethod(Method m){
        methods.add(m);
    }

    public void addField(java.lang.reflect.Field f){
        fields.add(f);
    }

    public void setClass(Class<?> c){
        this.c = c;
    }

    public void setMethods(Set<Method> m){
        methods = m;
    }

    public void setFields(Set<java.lang.reflect.Field> f){
        fields = f;
    }

    public Class<?> getControllerClass(){
        return c;
    }

    public Set<Method> getMethods(){
        return methods;
    }

    public Set<java.lang.reflect.Field> getFields(){
        return fields;
    }
}