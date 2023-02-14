package com.oracle.svm.hosted.prophet.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

public class Component{
    private Set<?> layer;

    public Component(Set<?> layer){
        verifyLayerType();
        this.layer = layer;
    }

    public Set<?> getLayer(){
        return this.layer;
    }

    public void setLayer(Set<?> layer){
        this.layer = layer;
    }

    // private verifyLayerType(Set<?> layer) throws RuntimeException{
    private void verifyLayerType(){
        try{
            
        }catch(Exception err){
            System.err.println(err);
        }
    }
}
