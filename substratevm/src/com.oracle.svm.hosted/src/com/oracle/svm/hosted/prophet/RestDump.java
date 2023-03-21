package com.oracle.svm.hosted.prophet;
import com.oracle.svm.hosted.prophet.model.Endpoint;
import com.oracle.svm.hosted.prophet.model.RestCall;

import java.io.IOException;
import java.util.Set;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.RuntimeException;

import java.io.FileWriter;

public class RestDump {
    
    //RESTCALL CSV ORDER SHOULD BE
    //msName, restCallInClassName, parentMethod, uri, httpMethod, returnType, isCollection
    //ENDPOINT CSV ORDER SHOULD BE 
    //msName, endpointInClassName, parentMethod, path, httpMethod, returnType, isCollection, arguments

    public void writeOutRestCalls(Set<RestCall> restCalls, String outputFile){

        if (outputFile == null){
            throw new RuntimeException("ProphetRestCallOutputFile option was not provided");
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))){
            for (RestCall rc : restCalls){
                writer.write(rc.toString() + "\n");
            }
        }catch(IOException ex){
            ex.printStackTrace();
        }

    }
    public void writeOutEndpoints(Set<Endpoint> endpoints, String outputFile){
        if (outputFile == null){
            throw new RuntimeException("ProphetEndpointOutputFile option was not provided");
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))){
            for (Endpoint ep : endpoints){
                writer.write(ep.toString() + "\n");
            }
        }catch(IOException ex){
            ex.printStackTrace();
        }
    }
}
