package com.example.myapplication.model

enum class DraftOwnerType {
    GUEST,
    USER;

    companion object {
        fun fromStorageValue(value: String): DraftOwnerType {
            return entries.firstOrNull { it.name == value } ?: GUEST
        }
    }
}
