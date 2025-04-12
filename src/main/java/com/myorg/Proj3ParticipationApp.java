package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.StackProps;

public class Proj3ParticipationApp {
    public static void main(final String[] args) {
        App app = new App();
        new Proj3ParticipationAppStack(app, "Proj3Stack", StackProps.builder().build());
        app.synth();
    }
}