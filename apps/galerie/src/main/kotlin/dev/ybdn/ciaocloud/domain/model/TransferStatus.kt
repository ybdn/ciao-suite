package dev.ybdn.ciaocloud.domain.model

enum class TransferStatus {
    PENDING,
    COPYING,
    COPIED,
    VERIFIED,
    FAILED,
    DELETED,
}
