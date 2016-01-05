suite = {
  "mxversion" : "5.5.12",
  "name" : "jvmci",
  "url" : "http://openjdk.java.net/projects/graal",
  "developer" : {
    "name" : "Truffle and Graal developers",
    "email" : "graal-dev@openjdk.java.net",
    "organization" : "Graal",
    "organizationUrl" : "http://openjdk.java.net/projects/graal",
  },
  "repositories" : {
    "lafo-snapshots" : {
      "url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots",
      "licenses" : ["GPLv2-CPE", "UPL"]
    },
  },

  "licenses" : {
    "UPL" : {
      "name" : "Universal Permissive License, Version 1.0",
      "url" : "http://opensource.org/licenses/UPL",
    }
  },

  "defaultLicense" : "GPLv2-CPE",

  # This puts mx/ as a sibiling of the JDK build configuration directories
  # (e.g., macosx-x86_64-normal-server-release).
  "outputRoot" : "../build/mx/hotspot",

    # ------------- Libraries -------------

  "libraries" : {

    # ------------- Libraries -------------

    "HCFDIS" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/hcfdis-3.jar"],
      "sha1" : "a71247c6ddb90aad4abf7c77e501acc60674ef57",
    },

    "C1VISUALIZER_DIST" : {
      "urls" : ["https://java.net/downloads/c1visualizer/c1visualizer_2015-07-22.zip"],
      "sha1" : "7ead6b2f7ed4643ef4d3343a5562e3d3f39564ac",
    },

    "JOL_INTERNALS" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/truffle/jol/jol-internals.jar"],
      "sha1" : "508bcd26a4d7c4c44048990c6ea789a3b11a62dc",
    },

    "BATIK" : {
      "sha1" : "122b87ca88e41a415cf8b523fd3d03b4325134a3",
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/batik-all-1.7.jar"],
    },
  },

  "projects" : {

    # ------------- JVMCI:Service -------------

    "jdk.vm.ci.service" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "javaCompliance" : "1.8",
      "workingSets" : "API,JVMCI",
    },

    "jdk.vm.ci.service.processor" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : ["jdk.vm.ci.service"],
      "checkstyle" : "jdk.vm.ci.service",
      "javaCompliance" : "1.8",
      "workingSets" : "JVMCI,Codegen,HotSpot",
    },

    # ------------- JVMCI:API -------------

    "jdk.vm.ci.common" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "checkstyle" : "jdk.vm.ci.service",
      "javaCompliance" : "1.8",
      "workingSets" : "API,JVMCI",
    },

    "jdk.vm.ci.meta" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "checkstyle" : "jdk.vm.ci.service",
      "javaCompliance" : "1.8",
      "workingSets" : "API,JVMCI",
    },

    "jdk.vm.ci.code" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : ["jdk.vm.ci.meta"],
      "checkstyle" : "jdk.vm.ci.service",
      "javaCompliance" : "1.8",
      "workingSets" : "API,JVMCI",
    },

    "jdk.vm.ci.runtime" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.vm.ci.code",
      ],
      "checkstyle" : "jdk.vm.ci.service",
      "javaCompliance" : "1.8",
      "workingSets" : "API,JVMCI",
    },

    "jdk.vm.ci.runtime.test" : {
      "subDir" : "test/compiler/jvmci",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "jdk.vm.ci.common",
        "jdk.vm.ci.runtime",
      ],
      "checkstyle" : "jdk.vm.ci.service",
      "javaCompliance" : "1.8",
      "workingSets" : "API,JVMCI",
    },

    "jdk.vm.ci.inittimer" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "checkstyle" : "jdk.vm.ci.service",
      "javaCompliance" : "1.8",
      "workingSets" : "JVMCI",
    },

    # ------------- JVMCI:HotSpot -------------

    "jdk.vm.ci.aarch64" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : ["jdk.vm.ci.code"],
      "checkstyle" : "jdk.vm.ci.service",
      "javaCompliance" : "1.8",
      "workingSets" : "JVMCI,AArch64",
    },

    "jdk.vm.ci.amd64" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : ["jdk.vm.ci.code"],
      "checkstyle" : "jdk.vm.ci.service",
      "javaCompliance" : "1.8",
      "workingSets" : "JVMCI,AMD64",
    },

    "jdk.vm.ci.sparc" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : ["jdk.vm.ci.code"],
      "checkstyle" : "jdk.vm.ci.service",
      "javaCompliance" : "1.8",
      "workingSets" : "JVMCI,SPARC",
    },

    "jdk.vm.ci.hotspot" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.vm.ci.hotspotvmconfig",
        "jdk.vm.ci.common",
        "jdk.vm.ci.inittimer",
        "jdk.vm.ci.runtime",
        "jdk.vm.ci.service",
      ],
      "checkstyle" : "jdk.vm.ci.service",
      "javaCompliance" : "1.8",
      "workingSets" : "JVMCI",
    },

    "jdk.vm.ci.hotspotvmconfig" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "checkstyle" : "jdk.vm.ci.service",
      "javaCompliance" : "1.8",
      "workingSets" : "JVMCI,HotSpot",
    },

    "jdk.vm.ci.hotspot.aarch64" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.vm.ci.aarch64",
        "jdk.vm.ci.hotspot",
      ],
      "checkstyle" : "jdk.vm.ci.service",
      "annotationProcessors" : [
        "JVMCI_SERVICE_PROCESSOR",
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "JVMCI,HotSpot,AArch64",
    },

    "jdk.vm.ci.hotspot.amd64" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.vm.ci.amd64",
        "jdk.vm.ci.hotspot",
      ],
      "checkstyle" : "jdk.vm.ci.service",
      "annotationProcessors" : [
        "JVMCI_SERVICE_PROCESSOR",
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "JVMCI,HotSpot,AMD64",
    },

    "jdk.vm.ci.hotspot.sparc" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "jdk.vm.ci.sparc",
        "jdk.vm.ci.hotspot",
      ],
      "checkstyle" : "jdk.vm.ci.service",
      "annotationProcessors" : [
        "JVMCI_SERVICE_PROCESSOR",
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "JVMCI,HotSpot,SPARC",
    },

    "hotspot" : {
      "native" : True,
      "class" : "HotSpotProject",
    }
  },

  "distributions" : {

    # ------------- Distributions -------------

    "JVMCI_SERVICE" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "dependencies" : ["jdk.vm.ci.service"],
    },

    "JVMCI_API" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "dependencies" : [
        "jdk.vm.ci.inittimer",
        "jdk.vm.ci.runtime",
        "jdk.vm.ci.common",
        "jdk.vm.ci.aarch64",
        "jdk.vm.ci.amd64",
        "jdk.vm.ci.sparc",
      ],
      "distDependencies" : [
        "JVMCI_SERVICE",
      ],
    },

    "JVMCI_HOTSPOTVMCONFIG" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "dependencies" : [
        "jdk.vm.ci.hotspotvmconfig",
      ],
    },

    "JVMCI_HOTSPOT" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "dependencies" : [
        "jdk.vm.ci.hotspot.aarch64",
        "jdk.vm.ci.hotspot.amd64",
        "jdk.vm.ci.hotspot.sparc",
      ],
      "distDependencies" : [
        "JVMCI_HOTSPOTVMCONFIG",
        "JVMCI_SERVICE",
        "JVMCI_API",
      ],
    },

    "JVMCI_TEST" : {
      "subDir" : "test/compiler/jvmci",
      "dependencies" : [
        "jdk.vm.ci.runtime.test",
      ],
      "distDependencies" : [
        "JVMCI_API",
      ],
      "exclude" : ["mx:JUNIT"],
    },


    "JVMCI_SERVICE_PROCESSOR" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "dependencies" : ["jdk.vm.ci.service.processor"],
      "distDependencies" : [
        "JVMCI_SERVICE",
      ],
    },

    # This exists to have a monolithic jvmci.jar file which simplifies
    # using the -Xoverride option in JDK9.
    "JVMCI" : {
      "subDir" : "src/jdk.vm.ci/share/classes",
      "overlaps" : [
        "JVMCI_API",
        "JVMCI_SERVICE",
        "JVMCI_HOTSPOT",
        "JVMCI_HOTSPOTVMCONFIG",
        "JVMCI_SERVICE_PROCESSOR",
      ],
      "dependencies" : [
        "jdk.vm.ci.service",
        "jdk.vm.ci.inittimer",
        "jdk.vm.ci.runtime",
        "jdk.vm.ci.common",
        "jdk.vm.ci.aarch64",
        "jdk.vm.ci.amd64",
        "jdk.vm.ci.sparc",
        "jdk.vm.ci.hotspotvmconfig",
        "jdk.vm.ci.hotspot.aarch64",
        "jdk.vm.ci.hotspot.amd64",
        "jdk.vm.ci.hotspot.sparc",
        "jdk.vm.ci.service.processor"
      ],
    },
  },
}
