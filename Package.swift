// swift-tools-version:5.8
import PackageDescription

// Use the local binary if true
let useLocalBinary = false   // ← 手元のファイル（GitHubに直接コミットする実ファイル）を使う設定

// CI will replace the nils with the actual values when building a release
let version: String = "0.5.1"
let binaryURL: String =
    "https://github.com/papagrationbiz-sketch/valhalla-mobile/releases/download/\(version)/valhalla-wrapper.xcframework.zip"
let binaryChecksum: String = "400d18b4f198fd02e56615392e698c4391a2de6fd0a624d1388c2484d67f78a4"

// Binary target 設定
var binaryTarget: Target = .binaryTarget(
    name: "ValhallaWrapper",
    url: binaryURL,
    checksum: binaryChecksum
)

if useLocalBinary {
    binaryTarget = .binaryTarget(
        name: "ValhallaWrapper",
        path: "build/apple/valhalla-wrapper.xcframework"
    )
}

let package = Package(
    name: "ValhallaMobile",
    platforms: [
        .iOS("16.4")
        // .tvOS(.v13),
        // .watchOS(.v6),
        // .macOS(.v10_13)
    ],
    products: [
        .library(
            name: "Valhalla",
            targets: ["Valhalla"]
        )
    ],
    dependencies: [
        .package(
            url: "https://github.com/Rallista/valhalla-openapi-models-swift.git", .upToNextMinor(from: "0.2.0")),
        .package(url: "https://github.com/UInt2048/Light-Swift-Untar.git", .upToNextMajor(from: "1.0.4")),
        .package(url: "https://github.com/apple/swift-docc-plugin", .upToNextMajor(from: "1.0.0")),
    ],
    targets: [
        .target(
            name: "Valhalla",
            dependencies: [
                "ValhallaObjc",
                "ValhallaWrapper",
                .product(name: "ValhallaConfigModels", package: "valhalla-openapi-models-swift"),
                .product(name: "ValhallaModels", package: "valhalla-openapi-models-swift"),
                .product(name: "Light-Swift-Untar", package: "Light-Swift-Untar"),
            ],
            path: "apple/Sources/Valhalla",
            resources: [
                .process("SupportData")
            ]
        ),
        .target(
            name: "ValhallaObjc",
            dependencies: ["ValhallaWrapper"],
            path: "apple/Sources/ValhallaObjc",
            linkerSettings: [.linkedLibrary("z")]
        ),
        binaryTarget,
        .testTarget(
            name: "ValhallaTests",
            dependencies: ["Valhalla"],
            path: "apple/Tests/ValhallaTests",
            resources: [.copy("TestData")]
        ),
    ],
    cLanguageStandard: .gnu17,
    cxxLanguageStandard: .cxx20
)