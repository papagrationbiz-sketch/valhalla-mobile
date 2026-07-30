import XCTest
import CryptoKit
import Darwin
import ValhallaConfigModels
@testable import Valhalla

/// Produces `<outputDir>/performance.json`, matching the schema in
/// `contract-tests/examples/performance-baseline.json`, so iOS numbers can be
/// compared against Android's and against the historical baseline.
///
/// Note: `valhalla_commit` is deliberately NOT emitted here. CI injects it
/// after the fact (it knows the commit the wrapper binary was built from).
final class PerformanceTests: XCTestCase {
    func testPerformanceBaseline() throws {
        let manifest = try ContractFixtures.loadManifest()
        let outputDir = try ContractFixtures.ensureOutputDir()

        let requestJSON = try ContractFixtures.loadRequest("requests/route-auto.json")
        let config = try ContractFixtures.makeConfig()

        // Cold call: first route() on a freshly constructed instance. Its
        // response isn't part of the 100-iteration `failures` count below;
        // it's only used to compute `cold_route_ms`.
        let valhalla = try Valhalla(config)
        let coldStart = DispatchTime.now()
        _ = valhalla.route(rawRequest: requestJSON)
        let coldEnd = DispatchTime.now()
        let coldRouteMs = Self.milliseconds(from: coldStart, to: coldEnd)

        // Warm calls: 100 further calls on the SAME instance.
        var failures = 0
        var warmDurationsMs: [Double] = []
        warmDurationsMs.reserveCapacity(100)
        for _ in 0..<100 {
            let start = DispatchTime.now()
            let response = valhalla.route(rawRequest: requestJSON)
            let end = DispatchTime.now()
            warmDurationsMs.append(Self.milliseconds(from: start, to: end))
            if ContractFixtures.responseIndicatesError(response) {
                failures += 1
            }
        }

        let warmRouteMsP50 = Self.median(of: warmDurationsMs)

        guard let peakRSSBytes = Self.peakResidentSizeBytes() else {
            XCTFail("Unable to read peak resident size via task_info(MACH_TASK_BASIC_INFO)")
            return
        }
        let peakRSSMB = Double(peakRSSBytes) / 1024.0 / 1024.0

        let tileSha256 = try Self.sha256Hex(of: ContractFixtures.tilesTarURL)

        let record: [String: Any] = [
            "schema_version": 1,
            "platform": "ios",
            "fixture_id": manifest.fixtureId,
            "tile_sha256": tileSha256,
            "metrics": [
                "cold_route_ms": coldRouteMs,
                "warm_route_ms_p50": warmRouteMsP50,
                "peak_rss_mb": peakRSSMB,
                "iterations": 100,
                "failures": failures,
            ],
        ]

        let data = try JSONSerialization.data(withJSONObject: record, options: [.sortedKeys])
        let outputURL = outputDir.appendingPathComponent("performance.json")
        try data.write(to: outputURL)
        print("Performance results: \(outputURL.path)")
    }

    private static func milliseconds(from start: DispatchTime, to end: DispatchTime) -> Double {
        Double(end.uptimeNanoseconds - start.uptimeNanoseconds) / 1_000_000.0
    }

    /// Median of an even-or-odd-sized sample; for an even count this is the
    /// mean of the two middle values.
    private static func median(of values: [Double]) -> Double {
        let sorted = values.sorted()
        let count = sorted.count
        if count % 2 == 0 {
            return (sorted[count / 2 - 1] + sorted[count / 2]) / 2.0
        } else {
            return sorted[count / 2]
        }
    }

    /// Peak resident set size in bytes, via `task_info(MACH_TASK_BASIC_INFO)`.
    /// Returns nil if the mach call itself fails (caller must fail the test
    /// rather than emit a fabricated value).
    private static func peakResidentSizeBytes() -> UInt64? {
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size / MemoryLayout<integer_t>.size)
        let result = withUnsafeMutablePointer(to: &info) { infoPointer -> kern_return_t in
            infoPointer.withMemoryRebound(to: integer_t.self, capacity: Int(count)) { intPointer in
                task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO), intPointer, &count)
            }
        }
        guard result == KERN_SUCCESS else { return nil }
        return info.resident_size_max
    }

    /// Lowercase hex SHA-256 of a file, streamed in chunks rather than
    /// loaded into memory as one `Data`.
    private static func sha256Hex(of url: URL) throws -> String {
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }

        var hasher = SHA256()
        while true {
            let chunk = try handle.read(upToCount: 1 << 20) // 1 MiB
            guard let chunk, !chunk.isEmpty else { break }
            hasher.update(data: chunk)
        }
        return hasher.finalize().map { String(format: "%02x", $0) }.joined()
    }
}
