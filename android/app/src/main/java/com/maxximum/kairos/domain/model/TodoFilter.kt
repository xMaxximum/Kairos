package com.maxximum.kairos.domain.model

enum class TodoFilter(val label: String) {
    ALL("All Tasks"),
    PENDING("Pending"),
    COMPLETED("Completed"),
    ARCHIVED("Archived")
}
