package dev.staticsanches.kge.rasterizer.utils

import dev.staticsanches.kge.math.vector.Int2D
import dev.staticsanches.kge.math.vector.by
import dev.staticsanches.kge.rasterizer.Viewport
import org.junit.jupiter.api.Nested
import kotlin.test.Test
import kotlin.test.assertEquals

class BresenhamLineTest {
    @Nested
    inner class Horizontal {
        @Nested
        inner class FromLeftToRight {
            @Test
            fun unbounded() =
                check(Viewport.Unbounded, listOf(-1 by 3, 0 by 3, 1 by 3, 2 by 3, 3 by 3, 4 by 3, 5 by 3, 6 by 3))

            @Test
            fun `lower bounded`() =
                check(
                    object : Viewport.LowerBounded {
                        override val lowerBoundInclusive: Int2D = 1 by 1
                    },
                    listOf(1 by 3, 2 by 3, 3 by 3, 4 by 3, 5 by 3, 6 by 3),
                )

            @Test
            fun `upper bounded`() =
                check(
                    object : Viewport.UpperBounded {
                        override val upperBoundExclusive: Int2D = 5 by 5
                    },
                    listOf(-1 by 3, 0 by 3, 1 by 3, 2 by 3, 3 by 3, 4 by 3),
                )

            @Test
            fun bounded() =
                check(
                    object : Viewport.Bounded {
                        override val lowerBoundInclusive: Int2D = 1 by 1
                        override val upperBoundExclusive: Int2D = 5 by 5
                    },
                    listOf(1 by 3, 2 by 3, 3 by 3, 4 by 3),
                )

            private fun check(
                viewport: Viewport,
                expected: List<Int2D>,
            ) = check(-1 by 3, 6 by 3, viewport, expected)
        }

        @Nested
        inner class FromRightToLeft {
            @Test
            fun unbounded() =
                check(
                    Viewport.Unbounded,
                    listOf(6 by 3, 5 by 3, 4 by 3, 3 by 3, 2 by 3, 1 by 3, 0 by 3, -1 by 3),
                )

            @Test
            fun `lower bounded`() =
                check(
                    object : Viewport.LowerBounded {
                        override val lowerBoundInclusive: Int2D = 1 by 1
                    },
                    listOf(6 by 3, 5 by 3, 4 by 3, 3 by 3, 2 by 3, 1 by 3),
                )

            @Test
            fun `upper bounded`() =
                check(
                    object : Viewport.UpperBounded {
                        override val upperBoundExclusive: Int2D = 5 by 5
                    },
                    listOf(4 by 3, 3 by 3, 2 by 3, 1 by 3, 0 by 3, -1 by 3),
                )

            @Test
            fun bounded() =
                check(
                    object : Viewport.Bounded {
                        override val lowerBoundInclusive: Int2D = 1 by 1
                        override val upperBoundExclusive: Int2D = 5 by 5
                    },
                    listOf(4 by 3, 3 by 3, 2 by 3, 1 by 3),
                )

            private fun check(
                viewport: Viewport,
                expected: List<Int2D>,
            ) = check(6 by 3, -1 by 3, viewport, expected)
        }
    }

    @Nested
    inner class Vertical {
        @Nested
        inner class FromTopToBottom {
            @Test
            fun unbounded() =
                check(
                    Viewport.Unbounded,
                    listOf(3 by -1, 3 by 0, 3 by 1, 3 by 2, 3 by 3, 3 by 4, 3 by 5, 3 by 6),
                )

            @Test
            fun `lower bounded`() =
                check(
                    object : Viewport.LowerBounded {
                        override val lowerBoundInclusive: Int2D = 1 by 1
                    },
                    listOf(3 by 1, 3 by 2, 3 by 3, 3 by 4, 3 by 5, 3 by 6),
                )

            @Test
            fun `upper bounded`() =
                check(
                    object : Viewport.UpperBounded {
                        override val upperBoundExclusive: Int2D = 5 by 5
                    },
                    listOf(3 by -1, 3 by 0, 3 by 1, 3 by 2, 3 by 3, 3 by 4),
                )

            @Test
            fun bounded() =
                check(
                    object : Viewport.Bounded {
                        override val lowerBoundInclusive: Int2D = 1 by 1
                        override val upperBoundExclusive: Int2D = 5 by 5
                    },
                    listOf(3 by 1, 3 by 2, 3 by 3, 3 by 4),
                )

            private fun check(
                viewport: Viewport,
                expected: List<Int2D>,
            ) = check(3 by -1, 3 by 6, viewport, expected)
        }

        @Nested
        inner class FromBottomToTop {
            @Test
            fun unbounded() =
                check(
                    Viewport.Unbounded,
                    listOf(3 by 6, 3 by 5, 3 by 4, 3 by 3, 3 by 2, 3 by 1, 3 by 0, 3 by -1),
                )

            @Test
            fun `lower bounded`() =
                check(
                    object : Viewport.LowerBounded {
                        override val lowerBoundInclusive: Int2D = 1 by 1
                    },
                    listOf(3 by 6, 3 by 5, 3 by 4, 3 by 3, 3 by 2, 3 by 1),
                )

            @Test
            fun `upper bounded`() =
                check(
                    object : Viewport.UpperBounded {
                        override val upperBoundExclusive: Int2D = 5 by 5
                    },
                    listOf(3 by 4, 3 by 3, 3 by 2, 3 by 1, 3 by 0, 3 by -1),
                )

            @Test
            fun bounded() =
                check(
                    object : Viewport.Bounded {
                        override val lowerBoundInclusive: Int2D = 1 by 1
                        override val upperBoundExclusive: Int2D = 5 by 5
                    },
                    listOf(3 by 4, 3 by 3, 3 by 2, 3 by 1),
                )

            private fun check(
                viewport: Viewport,
                expected: List<Int2D>,
            ) = check(3 by 6, 3 by -1, viewport, expected)
        }
    }

    @Nested
    inner class Diagonal {
        @Nested
        inner class FromSouthWestToNorthEast {
            @Test
            fun unbounded() =
                check(
                    Viewport.Unbounded,
                    listOf(-1 by 7, 0 by 6, 1 by 5, 2 by 4, 3 by 3, 4 by 2, 5 by 1, 6 by 0, 7 by -1),
                )

            @Test
            fun `lower bounded`() =
                check(
                    object : Viewport.LowerBounded {
                        override val lowerBoundInclusive: Int2D = 1 by 1
                    },
                    listOf(1 by 5, 2 by 4, 3 by 3, 4 by 2, 5 by 1),
                )

            @Test
            fun `upper bounded`() =
                check(
                    object : Viewport.UpperBounded {
                        override val upperBoundExclusive: Int2D = 5 by 5
                    },
                    listOf(2 by 4, 3 by 3, 4 by 2),
                )

            @Test
            fun bounded() =
                check(
                    object : Viewport.Bounded {
                        override val lowerBoundInclusive: Int2D = 1 by 3
                        override val upperBoundExclusive: Int2D = 5 by 5
                    },
                    listOf(2 by 4, 3 by 3),
                )

            private fun check(
                viewport: Viewport,
                expected: List<Int2D>,
            ) = check(-1 by 7, 7 by -1, viewport, expected)
        }

        @Nested
        inner class FromNorthEastToSouthWest {
            @Test
            fun unbounded() =
                check(
                    Viewport.Unbounded,
                    listOf(7 by -1, 6 by 0, 5 by 1, 4 by 2, 3 by 3, 2 by 4, 1 by 5, 0 by 6, -1 by 7),
                )

            @Test
            fun `lower bounded`() =
                check(
                    object : Viewport.LowerBounded {
                        override val lowerBoundInclusive: Int2D = 1 by 1
                    },
                    listOf(5 by 1, 4 by 2, 3 by 3, 2 by 4, 1 by 5),
                )

            @Test
            fun `upper bounded`() =
                check(
                    object : Viewport.UpperBounded {
                        override val upperBoundExclusive: Int2D = 5 by 5
                    },
                    listOf(4 by 2, 3 by 3, 2 by 4),
                )

            @Test
            fun bounded() =
                check(
                    object : Viewport.Bounded {
                        override val lowerBoundInclusive: Int2D = 1 by 3
                        override val upperBoundExclusive: Int2D = 5 by 5
                    },
                    listOf(3 by 3, 2 by 4),
                )

            private fun check(
                viewport: Viewport,
                expected: List<Int2D>,
            ) = check(7 by -1, -1 by 7, viewport, expected)
        }

        @Nested
        inner class FromNorthWestToSouthEast {
            @Test
            fun unbounded() =
                check(
                    Viewport.Unbounded,
                    listOf(-3 by 2, -2 by 3, -1 by 4, 0 by 5, 1 by 6, 2 by 7, 3 by 8, 4 by 9, 5 by 10),
                )

            @Test
            fun `lower bounded`() =
                check(
                    object : Viewport.LowerBounded {
                        override val lowerBoundInclusive: Int2D = 1 by 1
                    },
                    listOf(1 by 6, 2 by 7, 3 by 8, 4 by 9, 5 by 10),
                )

            @Test
            fun `upper bounded`() =
                check(
                    object : Viewport.UpperBounded {
                        override val upperBoundExclusive: Int2D = 5 by 5
                    },
                    listOf(-3 by 2, -2 by 3, -1 by 4),
                )

            @Test
            fun bounded() =
                check(
                    object : Viewport.Bounded {
                        override val lowerBoundInclusive: Int2D = -1 by 3
                        override val upperBoundExclusive: Int2D = 5 by 7
                    },
                    listOf(-1 by 4, 0 by 5, 1 by 6),
                )

            private fun check(
                viewport: Viewport,
                expected: List<Int2D>,
            ) = check(-3 by 2, 5 by 10, viewport, expected)
        }

        @Nested
        inner class FromSouthEastToNorthWest {
            @Test
            fun unbounded() =
                check(
                    Viewport.Unbounded,
                    listOf(5 by 10, 4 by 9, 3 by 8, 2 by 7, 1 by 6, 0 by 5, -1 by 4, -2 by 3, -3 by 2),
                )

            @Test
            fun `lower bounded`() =
                check(
                    object : Viewport.LowerBounded {
                        override val lowerBoundInclusive: Int2D = 1 by 1
                    },
                    listOf(5 by 10, 4 by 9, 3 by 8, 2 by 7, 1 by 6),
                )

            @Test
            fun `upper bounded`() =
                check(
                    object : Viewport.UpperBounded {
                        override val upperBoundExclusive: Int2D = 5 by 9
                    },
                    listOf(3 by 8, 2 by 7, 1 by 6, 0 by 5, -1 by 4, -2 by 3, -3 by 2),
                )

            @Test
            fun bounded() =
                check(
                    object : Viewport.Bounded {
                        override val lowerBoundInclusive: Int2D = -1 by 3
                        override val upperBoundExclusive: Int2D = 5 by 9
                    },
                    listOf(3 by 8, 2 by 7, 1 by 6, 0 by 5, -1 by 4),
                )

            private fun check(
                viewport: Viewport,
                expected: List<Int2D>,
            ) = check(5 by 10, -3 by 2, viewport, expected)
        }
    }

    @Nested
    inner class Funky {
        @Nested
        inner class WithSlopeLesserThan1 {
            @Nested
            inner class FromSouthWestToNorthEast {
                @Test
                fun unbounded() =
                    check(
                        Viewport.Unbounded,
                        listOf(1 by 4, 2 by 4, 3 by 4, 4 by 3, 5 by 3, 6 by 3, 7 by 3, 8 by 2, 9 by 2),
                    )

                @Test
                fun `lower bounded`() =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 2 by 3
                        },
                        listOf(2 by 4, 3 by 4, 4 by 3, 5 by 3, 6 by 3, 7 by 3),
                    )

                @Test
                fun `upper bounded`() =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 8 by 4
                        },
                        listOf(4 by 3, 5 by 3, 6 by 3, 7 by 3),
                    )

                @Test
                fun bounded() =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 1 by 2
                            override val upperBoundExclusive: Int2D = 9 by 4
                        },
                        listOf(4 by 3, 5 by 3, 6 by 3, 7 by 3, 8 by 2),
                    )

                private fun check(
                    viewport: Viewport,
                    expected: List<Int2D>,
                ) = check(1 by 4, 9 by 2, viewport, expected)
            }

            @Nested
            inner class FromNorthEastToSouthWest {
                @Test
                fun unbounded() =
                    check(
                        Viewport.Unbounded,
                        listOf(9 by 2, 8 by 2, 7 by 3, 6 by 3, 5 by 3, 4 by 3, 3 by 4, 2 by 4, 1 by 4),
                    )

                @Test
                fun `lower bounded`() =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 2 by 3
                        },
                        listOf(7 by 3, 6 by 3, 5 by 3, 4 by 3, 3 by 4, 2 by 4),
                    )

                @Test
                fun `upper bounded`() =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 8 by 4
                        },
                        listOf(7 by 3, 6 by 3, 5 by 3, 4 by 3),
                    )

                @Test
                fun bounded() =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 1 by 2
                            override val upperBoundExclusive: Int2D = 9 by 4
                        },
                        listOf(8 by 2, 7 by 3, 6 by 3, 5 by 3, 4 by 3),
                    )

                private fun check(
                    viewport: Viewport,
                    expected: List<Int2D>,
                ) = check(9 by 2, 1 by 4, viewport, expected)
            }

            @Nested
            inner class FromNorthWestToSouthEast {
                @Test
                fun unbounded() =
                    check(
                        Viewport.Unbounded,
                        listOf(2 by 3, 3 by 3, 4 by 4, 5 by 4, 6 by 5, 7 by 5, 8 by 6, 9 by 6, 10 by 7),
                    )

                @Test
                fun `lower bounded`() =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 5 by 3
                        },
                        listOf(5 by 4, 6 by 5, 7 by 5, 8 by 6, 9 by 6, 10 by 7),
                    )

                @Test
                fun `upper bounded`() =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 9 by 7
                        },
                        listOf(2 by 3, 3 by 3, 4 by 4, 5 by 4, 6 by 5, 7 by 5, 8 by 6),
                    )

                @Test
                fun bounded() =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 5 by 3
                            override val upperBoundExclusive: Int2D = 9 by 7
                        },
                        listOf(5 by 4, 6 by 5, 7 by 5, 8 by 6),
                    )

                private fun check(
                    viewport: Viewport,
                    expected: List<Int2D>,
                ) = check(2 by 3, 10 by 7, viewport, expected)
            }

            @Nested
            inner class FromSouthEastToNorthWest {
                @Test
                fun unbounded() =
                    check(
                        Viewport.Unbounded,
                        listOf(10 by 7, 9 by 6, 8 by 6, 7 by 5, 6 by 5, 5 by 4, 4 by 4, 3 by 3, 2 by 3),
                    )

                @Test
                fun `lower bounded`() =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 5 by 3
                        },
                        listOf(10 by 7, 9 by 6, 8 by 6, 7 by 5, 6 by 5, 5 by 4),
                    )

                @Test
                fun `upper bounded`() =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 9 by 7
                        },
                        listOf(8 by 6, 7 by 5, 6 by 5, 5 by 4, 4 by 4, 3 by 3, 2 by 3),
                    )

                @Test
                fun bounded() =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 5 by 3
                            override val upperBoundExclusive: Int2D = 9 by 7
                        },
                        listOf(8 by 6, 7 by 5, 6 by 5, 5 by 4),
                    )

                private fun check(
                    viewport: Viewport,
                    expected: List<Int2D>,
                ) = check(10 by 7, 2 by 3, viewport, expected)
            }
        }

        @Nested
        inner class WithSlopeGreaterThan1 {
            @Nested
            inner class FromSouthWestToNorthEast {
                @Test
                fun unbounded() =
                    check(
                        Viewport.Unbounded,
                        listOf(2 by 9, 2 by 8, 3 by 7, 3 by 6, 3 by 5, 4 by 4, 4 by 3, 5 by 2, 5 by 1),
                    )

                @Test
                fun `lower bounded`() =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 3 by 2
                        },
                        listOf(3 by 7, 3 by 6, 3 by 5, 4 by 4, 4 by 3, 5 by 2),
                    )

                @Test
                fun `upper bounded`() =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 5 by 9
                        },
                        listOf(2 by 8, 3 by 7, 3 by 6, 3 by 5, 4 by 4, 4 by 3),
                    )

                @Test
                fun bounded() =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 3 by 2
                            override val upperBoundExclusive: Int2D = 5 by 9
                        },
                        listOf(3 by 7, 3 by 6, 3 by 5, 4 by 4, 4 by 3),
                    )

                private fun check(
                    viewport: Viewport,
                    expected: List<Int2D>,
                ) = check(2 by 9, 5 by 1, viewport, expected)
            }

            @Nested
            inner class FromNorthEastToSouthWest {
                @Test
                fun unbounded() =
                    check(
                        Viewport.Unbounded,
                        listOf(5 by 1, 5 by 2, 4 by 3, 4 by 4, 3 by 5, 3 by 6, 3 by 7, 2 by 8, 2 by 9),
                    )

                @Test
                fun `lower bounded`() =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 3 by 2
                        },
                        listOf(5 by 2, 4 by 3, 4 by 4, 3 by 5, 3 by 6, 3 by 7),
                    )

                @Test
                fun `upper bounded`() =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 5 by 9
                        },
                        listOf(4 by 3, 4 by 4, 3 by 5, 3 by 6, 3 by 7, 2 by 8),
                    )

                @Test
                fun bounded() =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 3 by 2
                            override val upperBoundExclusive: Int2D = 5 by 9
                        },
                        listOf(4 by 3, 4 by 4, 3 by 5, 3 by 6, 3 by 7),
                    )

                private fun check(
                    viewport: Viewport,
                    expected: List<Int2D>,
                ) = check(5 by 1, 2 by 9, viewport, expected)
            }

            @Nested
            inner class FromNorthWestToSouthEast {
                @Test
                fun unbounded() =
                    check(
                        Viewport.Unbounded,
                        listOf(2 by 3, 2 by 4, 3 by 5, 3 by 6, 3 by 7, 4 by 8, 4 by 9, 5 by 10, 5 by 11),
                    )

                @Test
                fun `lower bounded`() =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 3 by 4
                        },
                        listOf(3 by 5, 3 by 6, 3 by 7, 4 by 8, 4 by 9, 5 by 10, 5 by 11),
                    )

                @Test
                fun `upper bounded`() =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 5 by 10
                        },
                        listOf(2 by 3, 2 by 4, 3 by 5, 3 by 6, 3 by 7, 4 by 8, 4 by 9),
                    )

                @Test
                fun bounded() =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 3 by 4
                            override val upperBoundExclusive: Int2D = 5 by 10
                        },
                        listOf(3 by 5, 3 by 6, 3 by 7, 4 by 8, 4 by 9),
                    )

                private fun check(
                    viewport: Viewport,
                    expected: List<Int2D>,
                ) = check(2 by 3, 5 by 11, viewport, expected)
            }

            @Nested
            inner class FromSouthEastToNorthWest {
                @Test
                fun unbounded() =
                    check(
                        Viewport.Unbounded,
                        listOf(5 by 11, 5 by 10, 4 by 9, 4 by 8, 3 by 7, 3 by 6, 3 by 5, 2 by 4, 2 by 3),
                    )

                @Test
                fun `lower bounded`() =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 3 by 4
                        },
                        listOf(5 by 11, 5 by 10, 4 by 9, 4 by 8, 3 by 7, 3 by 6, 3 by 5),
                    )

                @Test
                fun `upper bounded`() =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 5 by 10
                        },
                        listOf(4 by 9, 4 by 8, 3 by 7, 3 by 6, 3 by 5, 2 by 4, 2 by 3),
                    )

                @Test
                fun bounded() =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 3 by 4
                            override val upperBoundExclusive: Int2D = 5 by 10
                        },
                        listOf(4 by 9, 4 by 8, 3 by 7, 3 by 6, 3 by 5),
                    )

                private fun check(
                    viewport: Viewport,
                    expected: List<Int2D>,
                ) = check(5 by 11, 2 by 3, viewport, expected)
            }
        }
    }

    private fun check(
        first: Int2D,
        last: Int2D,
        viewport: Viewport,
        expected: List<Int2D>,
    ) {
        // Assert two times to check the independence of iterators
        assertEquals(expected, Iterable { BresenhamLine(first, last, viewport) }.toList())
    }
}
