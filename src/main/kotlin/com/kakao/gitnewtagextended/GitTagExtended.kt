package com.kakao.gitnewtagextended

data class GitTagExtended(
    val refname: String = "",
    val creator: String = "",
    val creatordate: String = "",
) {
    override fun toString(): String {
        return refname
    }
}
