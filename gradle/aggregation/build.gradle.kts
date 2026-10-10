plugins {
  id("software.sava.build.feature.publish-maven-central")
}

val idlClientModules = setOf(
  "core",
  "helidon",
  "jdk",
  "jetty",
  "netty",
  "sava"
)

dependencies {
  for (module in idlClientModules) {
    centralPortalAggregation(project(":http-servers-$module"))
  }
}
