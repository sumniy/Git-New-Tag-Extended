package com.kakao.gitnewtagextended

class GitTagExtendedComparator : Comparator<GitTagExtended> {
    override fun compare(o1: GitTagExtended, o2: GitTagExtended): Int {
        return o1.order.compareTo(o2.order)
    }
}