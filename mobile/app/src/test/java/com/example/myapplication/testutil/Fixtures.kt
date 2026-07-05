package com.example.myapplication.testutil

fun fixture(name: String): String {
    val stream = object {}.javaClass.classLoader
        ?.getResourceAsStream("fixtures/$name")
        ?: error("Fixture not found: fixtures/$name")
    return stream.bufferedReader().use { it.readText() }
}
