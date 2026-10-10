subprojects {
  plugins.withId("software.sava.build.feature.test") {
    tasks.withType<Test>().configureEach {
      // Keep captured output in test reports without streaming server logs to the console.
      testLogging.showStandardStreams = false
    }
  }
}
