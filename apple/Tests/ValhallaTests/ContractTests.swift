import XCTest
import ValhallaConfigModels
@testable import Valhalla

/// Shared fixture-path resolution for the cross-platform contract tests
/// (see `contract-tests/`). Used by both `ContractTests` and
/// `PerformanceTests` so there is a single source of truth for locating
/// the fixtures on disk instead of bundling a second copy as SPM resources.
enum ContractFixtures {
    /// Root of the git repository, derived from this file's location on disk.
    static let repoRoot: URL = {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()  // ValhallaTests
            .deletingLastPathComponent()  // Tests
            .deletingLastPathComponent()  // apple
            .deletingLastPathComponent()  // repo root
    }()

    static let fixturesRoot: URL = repoRoot.appendingPathComponent("contract-tests/fixtures")

    static let manifestURL: URL = fixturesRoot.appendingPathComponent("manifest.json")

    /// The same tile archive used by `TestValhallaWithTar`, loaded from the
    /// test bundle's copied `TestData` resource (the single on-disk copy of
    /// the tiles, not a second copy of the contract fixtures).
    static let tilesTarURL: URL = {
        Bundle.module.url(forResource: "TestData/valhalla_tiles", withExtension: "tar")!
    }()

    /// Directory contract-test results are written to. Overridable via the
    /// `CONTRACT_RESULTS_DIR` environment variable so CI can redirect output.
    static var outputDir: URL {
        if let override = ProcessInfo.processInfo.environment["CONTRACT_RESULTS_DIR"], !override.isEmpty {
            return URL(fileURLWithPath: override, isDirectory: true)
        }
        return repoRoot.appendingPathComponent("build/contract-results/ios")
    }

    /// Creates `outputDir` if it doesn't already exist and returns it.
    @discardableResult
    static func ensureOutputDir() throws -> URL {
        let dir = outputDir
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static func loadManifest() throws -> Manifest {
        let data = try Data(contentsOf: manifestURL)
        return try JSONDecoder().decode(Manifest.self, from: data)
    }

    /// Loads a fixture request file, given its path relative to `fixturesRoot`
    /// (as recorded in the manifest, e.g. "requests/route-auto.json").
    static func loadRequest(_ relativePath: String) throws -> String {
        let url = fixturesRoot.appendingPathComponent(relativePath)
        let data = try Data(contentsOf: url)
        guard let str = String(data: data, encoding: .utf8) else {
            throw ValhallaError.encodingNotUtf8("request:\(relativePath)")
        }
        return str
    }

    static func makeConfig() throws -> ValhallaConfig {
        try ValhallaConfig(tileExtractTar: tilesTarURL)
    }

    /// True if a raw JSON response has a top-level `error` key or a non-zero
    /// `trip.status`, per the contract-tests failure definition.
    static func responseIndicatesError(_ raw: String) -> Bool {
        guard let data = raw.data(using: .utf8),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return false
        }
        if json["error"] != nil {
            return true
        }
        if let trip = json["trip"] as? [String: Any], let status = trip["status"] as? Int, status != 0 {
            return true
        }
        return false
    }
}

/// Mirrors `contract-tests/fixtures/manifest.json`.
struct Manifest: Decodable {
    let schemaVersion: Int
    let fixtureId: String
    let cases: [ManifestCase]

    enum CodingKeys: String, CodingKey {
        case schemaVersion = "schema_version"
        case fixtureId = "fixture_id"
        case cases
    }
}

struct ManifestCase: Decodable {
    let id: String
    let endpoint: String
    let request: String
    let comparisonProfile: String
    let execution: Execution?

    enum CodingKeys: String, CodingKey {
        case id, endpoint, request
        case comparisonProfile = "comparison_profile"
        case execution
    }

    struct Execution: Decodable {
        let reuseCount: Int
        let reinitializeAfter: Bool

        enum CodingKeys: String, CodingKey {
            case reuseCount = "reuse_count"
            case reinitializeAfter = "reinitialize_after"
        }
    }
}

enum ContractTestError: Error {
    case unknownEndpoint(String)
}

/// Runs the shared contract-test fixtures (`contract-tests/fixtures/manifest.json`)
/// against the iOS raw-JSON API, so the responses can be diffed against Android's
/// for the same fixtures by the out-of-tree comparison tooling.
final class ContractTests: XCTestCase {
    /// Executes every case in the manifest, in manifest order, against a single
    /// shared `Valhalla` instance, and writes each raw response verbatim to
    /// `<outputDir>/<case_id>.json`.
    func testContractCases() throws {
        let manifest = try ContractFixtures.loadManifest()
        let outputDir = try ContractFixtures.ensureOutputDir()
        print("Contract test results: \(outputDir.path)")

        let config = try ContractFixtures.makeConfig()
        let valhalla = try Valhalla(config)

        for testCase in manifest.cases {
            let requestJSON = try ContractFixtures.loadRequest(testCase.request)
            let response: String

            if let execution = testCase.execution {
                // Actor-lifecycle case: exercised on its own dedicated
                // instance(s), independent of the shared `valhalla` used for
                // the other cases (mirrors the Android contract test).
                let lifecycleActor = try Valhalla(config)
                for _ in 0..<execution.reuseCount {
                    _ = try dispatch(testCase.endpoint, requestJSON, on: lifecycleActor)
                }
                if execution.reinitializeAfter {
                    let freshActor = try Valhalla(config)
                    response = try dispatch(testCase.endpoint, requestJSON, on: freshActor)
                } else {
                    response = try dispatch(testCase.endpoint, requestJSON, on: lifecycleActor)
                }
            } else {
                response = try dispatch(testCase.endpoint, requestJSON, on: valhalla)
            }

            if testCase.comparisonProfile != "error", ContractFixtures.responseIndicatesError(response) {
                XCTFail("Case \(testCase.id) (\(testCase.endpoint)) returned an error response: \(response)")
            }

            let outputURL = outputDir.appendingPathComponent("\(testCase.id).json")
            try Data(response.utf8).write(to: outputURL)
        }
    }

    private func dispatch(_ endpoint: String, _ requestJSON: String, on valhalla: Valhalla) throws -> String {
        switch endpoint {
        case "route":
            return valhalla.route(rawRequest: requestJSON)
        case "trace_attributes":
            return valhalla.traceAttributes(rawRequest: requestJSON)
        case "trace_route":
            return valhalla.traceRoute(rawRequest: requestJSON)
        default:
            throw ContractTestError.unknownEndpoint(endpoint)
        }
    }
}
