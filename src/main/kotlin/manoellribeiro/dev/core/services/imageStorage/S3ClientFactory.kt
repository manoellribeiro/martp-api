package manoellribeiro.dev.core.services.imageStorage

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.smithy.kotlin.runtime.client.config.RequestHttpChecksumConfig
import aws.smithy.kotlin.runtime.client.config.ResponseHttpChecksumConfig
import aws.smithy.kotlin.runtime.net.url.Url
import manoellribeiro.dev.core.config.StorageConfig

fun createS3Client(config: StorageConfig): S3Client = S3Client {
    region = config.region
    endpointUrl = Url.parse(config.endpoint)
    forcePathStyle = config.forcePathStyle
    credentialsProvider = StaticCredentialsProvider {
        accessKeyId = config.accessKey
        secretAccessKey = config.secretKey
    }
    // The SDK's default checksums are an AWS feature that S3-compatible servers don't all support
    requestChecksumCalculation = RequestHttpChecksumConfig.WHEN_REQUIRED
    responseChecksumValidation = ResponseHttpChecksumConfig.WHEN_REQUIRED
}
