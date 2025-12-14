package com.example.courseworkapp

enum class UserRole {
    ADMIN, USER, NONE
}

data class AddressDTO(
    val id: Int,
    val name: String,
    val groupId: Int,
    val hasPower: Boolean,
    val genStatus: String,
    val genAuto: Boolean,
    val isManualRun: Boolean
)