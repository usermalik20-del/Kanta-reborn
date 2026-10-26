import Foundation
import GeloKaraokeCore

public struct LegacyResolvedPayload: Sendable {
    public let recordIndex: Int
    public let startOffset: UInt64
    public let endOffset: UInt64
    public let byteCount: UInt64
    public let signature: LegacyPayloadSignature
    public let mediaKind: MediaKind

    public var preferredFileExtension: String {
        switch mediaKind {
        case .midi, .kar:
            return "mid"
        case .mp3:
            return "mp3"
        case .wav:
            return "wav"
        default:
            return "bin"
        }
    }
}

public enum ShubBlobPayloadResolveError:
    Error,
    Equatable,
    Sendable
{
    case blobSizeUnavailable
    case trailingTableUnavailable
    case offsetCorrelationRejected
    case recordIndexOutOfBounds(Int)
    case invalidSpan
    case unsupportedPayloadSignature(
        LegacyPayloadSignature
    )
    case cannotCreateTemporaryFile
    case truncatedPayload(
        expected: UInt64,
        actual: UInt64
    )
}

extension ShubBlobPayloadResolveError: LocalizedError {
    public var errorDescription: String? {
        switch self {
        case .blobSizeUnavailable:
            return "The legacy song container size could not be verified."
        case .trailingTableUnavailable:
            return "The legacy song index does not contain a supported payload table."
        case .offsetCorrelationRejected:
            return "The legacy song index does not safely map to this song container."
        case let .recordIndexOutOfBounds(index):
            return "The requested legacy song index is out of bounds (\(index))."
        case .invalidSpan:
            return "The selected legacy song points to an invalid payload range."
        case let .unsupportedPayloadSignature(signature):
            return "This legacy song payload format is not supported (\(signature.rawValue))."
        case .cannotCreateTemporaryFile:
            return "KANTA Karaoke could not create a temporary playback file."
        case let .truncatedPayload(expected, actual):
            return "The legacy song payload is incomplete (expected \(expected) bytes, read \(actual))."
        }
    }
}

public enum ShubBlobPayloadResolver {
    public static func resolve(
        indexData: Data,
        blobURL: URL,
        recordIndex: Int,
        prefixByteCount: Int = 16
    ) throws -> LegacyResolvedPayload {
        let spans = try ShubBlobSpanIndex.spans(
            indexData: indexData,
            blobURL: blobURL
        )

        guard spans.indices.contains(
            recordIndex
        ) else {
            throw ShubBlobPayloadResolveError
                .recordIndexOutOfBounds(
                    recordIndex
                )
        }

        return try resolve(
            blobURL: blobURL,
            span: spans[recordIndex],
            prefixByteCount: prefixByteCount
        )
    }

    public static func resolve(
        blobURL: URL,
        span: LegacyPayloadSpan,
        prefixByteCount: Int = 16
    ) throws -> LegacyResolvedPayload {
        guard span.endOffset
                > span.startOffset else {
            throw ShubBlobPayloadResolveError
                .invalidSpan
        }

        let handle = try FileHandle(
            forReadingFrom: blobURL
        )
        defer { try? handle.close() }

        try handle.seek(
            toOffset: span.startOffset
        )
        let blobSize = try handle.seekToEnd()
        guard span.endOffset <= blobSize else {
            throw ShubBlobPayloadResolveError.invalidSpan
        }
        try handle.seek(toOffset: span.startOffset)
        let requested = Int(min(
            UInt64(min(max(16, prefixByteCount), 64 * 1024)),
            span.byteCount
        ))
        let prefix =
            try handle.read(
                upToCount: requested
            ) ?? Data()
        let signature =
            ShubBlobPayloadProbe.signature(
                for: prefix
            )

        let mediaKind: MediaKind
        switch signature {
        case .standardMIDI:
            mediaKind = .midi

        case .wave:
            mediaKind = .wav

        case .mp3ID3:
            mediaKind = .mp3

        case .mkmdV1:
            _ = try ShubMKMDDecoder.readHeader(
                blobURL: blobURL,
                startOffset:
                    span.startOffset,
                spanByteCount:
                    span.byteCount
            )
            mediaKind = .midi

        case .zip, .gzip, .unknown:
            throw ShubBlobPayloadResolveError
                .unsupportedPayloadSignature(
                    signature
                )
        }

        return LegacyResolvedPayload(
            recordIndex: span.recordIndex,
            startOffset: span.startOffset,
            endOffset: span.endOffset,
            byteCount: span.byteCount,
            signature: signature,
            mediaKind: mediaKind
        )
    }

    public static func materialize(
        indexData: Data,
        blobURL: URL,
        recordIndex: Int,
        destinationDirectory: URL,
        chunkByteCount: Int = 64 * 1024
    ) throws -> (
        payload: LegacyResolvedPayload,
        url: URL
    ) {
        let spans = try ShubBlobSpanIndex.spans(
            indexData: indexData,
            blobURL: blobURL
        )

        guard spans.indices.contains(
            recordIndex
        ) else {
            throw ShubBlobPayloadResolveError
                .recordIndexOutOfBounds(
                    recordIndex
                )
        }

        return try materialize(
            blobURL: blobURL,
            span: spans[recordIndex],
            destinationDirectory:
                destinationDirectory,
            chunkByteCount:
                chunkByteCount
        )
    }

    public static func materialize(
        blobURL: URL,
        span: LegacyPayloadSpan,
        destinationDirectory: URL,
        chunkByteCount: Int = 64 * 1024
    ) throws -> (
        payload: LegacyResolvedPayload,
        url: URL
    ) {
        let payload = try resolve(
            blobURL: blobURL,
            span: span
        )

        let fileManager =
            FileManager.default
        try fileManager.createDirectory(
            at: destinationDirectory,
            withIntermediateDirectories: true
        )

        let destination =
            destinationDirectory
                .appendingPathComponent(
                    "shub-\(span.recordIndex)."
                        + payload
                            .preferredFileExtension
                )
        let temporary =
            destinationDirectory
                .appendingPathComponent(
                    ".\(UUID().uuidString).tmp"
                )

        if fileManager.fileExists(
            atPath: temporary.path
        ) {
            try fileManager.removeItem(
                at: temporary
            )
        }

        do {
            switch payload.signature {
            case .mkmdV1:
                let decodedMediaKind =
                    try ShubMKMDDecoder
                        .materialize(
                            blobURL: blobURL,
                            startOffset:
                                payload
                                    .startOffset,
                            spanByteCount:
                                payload
                                    .byteCount,
                            destinationURL:
                                temporary
                        )

                guard decodedMediaKind
                        == payload.mediaKind else {
                    throw ShubMKMDDecodeError
                        .unsupportedDecodedSignature(
                            .unknown
                        )
                }

            case .standardMIDI,
                 .wave,
                 .mp3ID3:
                guard fileManager
                    .createFile(
                        atPath:
                            temporary.path,
                        contents: nil
                    ) else {
                    throw ShubBlobPayloadResolveError
                        .cannotCreateTemporaryFile
                }

                try copy(
                    blobURL: blobURL,
                    startOffset:
                        payload.startOffset,
                    byteCount:
                        payload.byteCount,
                    destinationURL:
                        temporary,
                    chunkByteCount:
                        max(
                            4 * 1024,
                            chunkByteCount
                        )
                )

            case .zip, .gzip, .unknown:
                throw ShubBlobPayloadResolveError
                    .unsupportedPayloadSignature(
                        payload.signature
                    )
            }

            if fileManager.fileExists(
                atPath: destination.path
            ) {
                try fileManager.removeItem(
                    at: destination
                )
            }
            try fileManager.moveItem(
                at: temporary,
                to: destination
            )
        } catch {
            try? fileManager.removeItem(
                at: temporary
            )
            throw error
        }

        return (
            payload,
            destination
        )
    }

    private static func copy(
        blobURL: URL,
        startOffset: UInt64,
        byteCount: UInt64,
        destinationURL: URL,
        chunkByteCount: Int
    ) throws {
        let input = try FileHandle(
            forReadingFrom: blobURL
        )
        let output = try FileHandle(
            forWritingTo: destinationURL
        )
        defer {
            try? input.close()
            try? output.close()
        }

        try input.seek(
            toOffset: startOffset
        )

        var remaining = byteCount
        var written: UInt64 = 0

        while remaining > 0 {
            let request = min(
                UInt64(chunkByteCount),
                remaining
            )
            let chunk =
                try input.read(
                    upToCount: Int(request)
                ) ?? Data()

            guard !chunk.isEmpty else {
                throw ShubBlobPayloadResolveError
                    .truncatedPayload(
                        expected: byteCount,
                        actual: written
                    )
            }

            try output.write(
                contentsOf: chunk
            )
            written += UInt64(
                chunk.count
            )
            remaining -= UInt64(
                chunk.count
            )
        }

        guard written == byteCount else {
            throw ShubBlobPayloadResolveError
                .truncatedPayload(
                    expected: byteCount,
                    actual: written
                )
        }
    }
}


