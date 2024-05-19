package dev.staticsanches.kge.rasterizer.utils

import dev.staticsanches.kge.rasterizer.Viewport
import dev.staticsanches.kge.types.vector.Int2D
import dev.staticsanches.kge.types.vector.by
import org.junit.jupiter.api.Nested
import kotlin.test.Test
import kotlin.test.assertEquals

class BresenhamLineTest {
    @Nested
    inner class Horizontal {
        @Nested
        inner class FromLeftToRight {
            @Nested
            inner class Unbounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        Viewport.Unbounded,
                        enforceOrder,
                        listOf(-1 by 3, 0 by 3, 1 by 3, 2 by 3, 3 by 3, 4 by 3, 5 by 3, 6 by 3),
                    )
            }

            @Nested
            inner class LowerBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 1 by 1
                        },
                        enforceOrder,
                        listOf(1 by 3, 2 by 3, 3 by 3, 4 by 3, 5 by 3, 6 by 3),
                    )
            }

            @Nested
            inner class UpperBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 5 by 5
                        },
                        enforceOrder,
                        listOf(-1 by 3, 0 by 3, 1 by 3, 2 by 3, 3 by 3, 4 by 3),
                    )
            }

            @Nested
            inner class Bounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 1 by 1
                            override val upperBoundExclusive: Int2D = 5 by 5
                        },
                        enforceOrder,
                        listOf(1 by 3, 2 by 3, 3 by 3, 4 by 3),
                    )
            }

            private fun check(
                viewport: Viewport,
                enforceOrder: Boolean,
                expected: List<Int2D>,
            ) = check(-1 by 3, 6 by 3, viewport, enforceOrder, expected)
        }

        @Nested
        inner class FromRightToLeft {
            @Nested
            inner class Unbounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        Viewport.Unbounded,
                        enforceOrder,
                        listOf(6 by 3, 5 by 3, 4 by 3, 3 by 3, 2 by 3, 1 by 3, 0 by 3, -1 by 3),
                    )
            }

            @Nested
            inner class LowerBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 1 by 1
                        },
                        enforceOrder,
                        listOf(6 by 3, 5 by 3, 4 by 3, 3 by 3, 2 by 3, 1 by 3),
                    )
            }

            @Nested
            inner class UpperBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 5 by 5
                        },
                        enforceOrder,
                        listOf(4 by 3, 3 by 3, 2 by 3, 1 by 3, 0 by 3, -1 by 3),
                    )
            }

            @Nested
            inner class Bounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 1 by 1
                            override val upperBoundExclusive: Int2D = 5 by 5
                        },
                        enforceOrder,
                        listOf(4 by 3, 3 by 3, 2 by 3, 1 by 3),
                    )
            }

            private fun check(
                viewport: Viewport,
                enforceOrder: Boolean,
                expected: List<Int2D>,
            ) = check(6 by 3, -1 by 3, viewport, enforceOrder, expected)
        }
    }

    @Nested
    inner class Vertical {
        @Nested
        inner class FromTopToBottom {
            @Nested
            inner class Unbounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        Viewport.Unbounded,
                        enforceOrder,
                        listOf(3 by -1, 3 by 0, 3 by 1, 3 by 2, 3 by 3, 3 by 4, 3 by 5, 3 by 6),
                    )
            }

            @Nested
            inner class LowerBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 1 by 1
                        },
                        enforceOrder,
                        listOf(3 by 1, 3 by 2, 3 by 3, 3 by 4, 3 by 5, 3 by 6),
                    )
            }

            @Nested
            inner class UpperBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 5 by 5
                        },
                        enforceOrder,
                        listOf(3 by -1, 3 by 0, 3 by 1, 3 by 2, 3 by 3, 3 by 4),
                    )
            }

            @Nested
            inner class Bounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 1 by 1
                            override val upperBoundExclusive: Int2D = 5 by 5
                        },
                        enforceOrder,
                        listOf(3 by 1, 3 by 2, 3 by 3, 3 by 4),
                    )
            }

            private fun check(
                viewport: Viewport,
                enforceOrder: Boolean,
                expected: List<Int2D>,
            ) = check(3 by -1, 3 by 6, viewport, enforceOrder, expected)
        }

        @Nested
        inner class FromBottomToTop {
            @Nested
            inner class Unbounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        Viewport.Unbounded,
                        enforceOrder,
                        listOf(3 by 6, 3 by 5, 3 by 4, 3 by 3, 3 by 2, 3 by 1, 3 by 0, 3 by -1),
                    )
            }

            @Nested
            inner class LowerBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 1 by 1
                        },
                        enforceOrder,
                        listOf(3 by 6, 3 by 5, 3 by 4, 3 by 3, 3 by 2, 3 by 1),
                    )
            }

            @Nested
            inner class UpperBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 5 by 5
                        },
                        enforceOrder,
                        listOf(3 by 4, 3 by 3, 3 by 2, 3 by 1, 3 by 0, 3 by -1),
                    )
            }

            @Nested
            inner class Bounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 1 by 1
                            override val upperBoundExclusive: Int2D = 5 by 5
                        },
                        enforceOrder,
                        listOf(3 by 4, 3 by 3, 3 by 2, 3 by 1),
                    )
            }

            private fun check(
                viewport: Viewport,
                enforceOrder: Boolean,
                expected: List<Int2D>,
            ) = check(3 by 6, 3 by -1, viewport, enforceOrder, expected)
        }
    }

    @Nested
    inner class Diagonal {
        @Nested
        inner class FromSouthWestToNorthEast {
            @Nested
            inner class Unbounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        Viewport.Unbounded,
                        enforceOrder,
                        listOf(-1 by 7, 0 by 6, 1 by 5, 2 by 4, 3 by 3, 4 by 2, 5 by 1, 6 by 0, 7 by -1),
                    )
            }

            @Nested
            inner class LowerBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 1 by 1
                        },
                        enforceOrder,
                        listOf(1 by 5, 2 by 4, 3 by 3, 4 by 2, 5 by 1),
                    )
            }

            @Nested
            inner class UpperBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 5 by 5
                        },
                        enforceOrder,
                        listOf(2 by 4, 3 by 3, 4 by 2),
                    )
            }

            @Nested
            inner class Bounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 1 by 3
                            override val upperBoundExclusive: Int2D = 5 by 5
                        },
                        enforceOrder,
                        listOf(2 by 4, 3 by 3),
                    )
            }

            private fun check(
                viewport: Viewport,
                enforceOrder: Boolean,
                expected: List<Int2D>,
            ) = check(-1 by 7, 7 by -1, viewport, enforceOrder, expected)
        }

        @Nested
        inner class FromNorthEastToSouthWest {
            @Nested
            inner class Unbounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        Viewport.Unbounded,
                        enforceOrder,
                        listOf(7 by -1, 6 by 0, 5 by 1, 4 by 2, 3 by 3, 2 by 4, 1 by 5, 0 by 6, -1 by 7),
                    )
            }

            @Nested
            inner class LowerBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 1 by 1
                        },
                        enforceOrder,
                        listOf(5 by 1, 4 by 2, 3 by 3, 2 by 4, 1 by 5),
                    )
            }

            @Nested
            inner class UpperBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 5 by 5
                        },
                        enforceOrder,
                        listOf(4 by 2, 3 by 3, 2 by 4),
                    )
            }

            @Nested
            inner class Bounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 1 by 3
                            override val upperBoundExclusive: Int2D = 5 by 5
                        },
                        enforceOrder,
                        listOf(3 by 3, 2 by 4),
                    )
            }

            private fun check(
                viewport: Viewport,
                enforceOrder: Boolean,
                expected: List<Int2D>,
            ) = check(7 by -1, -1 by 7, viewport, enforceOrder, expected)
        }

        @Nested
        inner class FromNorthWestToSouthEast {
            @Nested
            inner class Unbounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        Viewport.Unbounded,
                        enforceOrder,
                        listOf(-3 by 2, -2 by 3, -1 by 4, 0 by 5, 1 by 6, 2 by 7, 3 by 8, 4 by 9, 5 by 10),
                    )
            }

            @Nested
            inner class LowerBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 1 by 1
                        },
                        enforceOrder,
                        listOf(1 by 6, 2 by 7, 3 by 8, 4 by 9, 5 by 10),
                    )
            }

            @Nested
            inner class UpperBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 5 by 5
                        },
                        enforceOrder,
                        listOf(-3 by 2, -2 by 3, -1 by 4),
                    )
            }

            @Nested
            inner class Bounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = -1 by 3
                            override val upperBoundExclusive: Int2D = 5 by 7
                        },
                        enforceOrder,
                        listOf(-1 by 4, 0 by 5, 1 by 6),
                    )
            }

            private fun check(
                viewport: Viewport,
                enforceOrder: Boolean,
                expected: List<Int2D>,
            ) = check(-3 by 2, 5 by 10, viewport, enforceOrder, expected)
        }

        @Nested
        inner class FromSouthEastToNorthWest {
            @Nested
            inner class Unbounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        Viewport.Unbounded,
                        enforceOrder,
                        listOf(5 by 10, 4 by 9, 3 by 8, 2 by 7, 1 by 6, 0 by 5, -1 by 4, -2 by 3, -3 by 2),
                    )
            }

            @Nested
            inner class LowerBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 1 by 1
                        },
                        enforceOrder,
                        listOf(5 by 10, 4 by 9, 3 by 8, 2 by 7, 1 by 6),
                    )
            }

            @Nested
            inner class UpperBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 5 by 9
                        },
                        enforceOrder,
                        listOf(3 by 8, 2 by 7, 1 by 6, 0 by 5, -1 by 4, -2 by 3, -3 by 2),
                    )
            }

            @Nested
            inner class Bounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = -1 by 3
                            override val upperBoundExclusive: Int2D = 5 by 9
                        },
                        enforceOrder,
                        listOf(3 by 8, 2 by 7, 1 by 6, 0 by 5, -1 by 4),
                    )
            }

            private fun check(
                viewport: Viewport,
                enforceOrder: Boolean,
                expected: List<Int2D>,
            ) = check(5 by 10, -3 by 2, viewport, enforceOrder, expected)
        }
    }

    @Nested
    inner class Funky {
        @Nested
        inner class FromSouthWestToNorthEast {
            @Nested
            inner class Unbounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        Viewport.Unbounded,
                        enforceOrder,
                        listOf(-1 by 7, 0 by 7, 1 by 6, 2 by 6, 3 by 6, 4 by 5, 5 by 5, 6 by 4, 7 by 4),
                    )
            }

            @Nested
            inner class LowerBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 1 by 1
                        },
                        enforceOrder,
                        listOf(1 by 6, 2 by 6, 3 by 6, 4 by 5, 5 by 5, 6 by 4, 7 by 4),
                    )
            }

            @Nested
            inner class UpperBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 6 by 7
                        },
                        enforceOrder,
                        listOf(1 by 6, 2 by 6, 3 by 6, 4 by 5, 5 by 5),
                    )
            }

            @Nested
            inner class Bounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 1 by 3
                            override val upperBoundExclusive: Int2D = 6 by 7
                        },
                        enforceOrder,
                        listOf(1 by 6, 2 by 6, 3 by 6, 4 by 5, 5 by 5),
                    )
            }

            private fun check(
                viewport: Viewport,
                enforceOrder: Boolean,
                expected: List<Int2D>,
            ) = check(-1 by 7, 7 by 4, viewport, enforceOrder, expected)
        }

        @Nested
        inner class FromNorthEastToSouthWest {
            @Nested
            inner class Unbounded {
                @Test
                fun `Enforcing order`() =
                    check(
                        Viewport.Unbounded,
                        true,
                        listOf(7 by 4, 6 by 4, 5 by 5, 4 by 5, 3 by 6, 2 by 6, 1 by 6, 0 by 7, -1 by 7),
                    )

                @Test
                fun `Not enforcing order`() =
                    check(
                        Viewport.Unbounded,
                        false,
                        listOf(-1 by 7, 0 by 7, 1 by 6, 2 by 6, 3 by 6, 4 by 5, 5 by 5, 6 by 4, 7 by 4),
                    )
            }

            @Nested
            inner class LowerBounded {
                @Test
                fun `Enforcing order`() =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 1 by 1
                        },
                        true,
                        listOf(7 by 4, 6 by 4, 5 by 5, 4 by 5, 3 by 6, 2 by 6, 1 by 6),
                    )

                @Test
                fun `Not enforcing order`() =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 1 by 1
                        },
                        false,
                        listOf(1 by 6, 2 by 6, 3 by 6, 4 by 5, 5 by 5, 6 by 4, 7 by 4),
                    )
            }

            @Nested
            inner class UpperBounded {
                @Test
                fun `Enforcing order`() =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 5 by 7
                        },
                        true,
                        listOf(4 by 5, 3 by 6, 2 by 6, 1 by 6),
                    )

                @Test
                fun `Not enforcing order`() =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 5 by 7
                        },
                        false,
                        listOf(1 by 6, 2 by 6, 3 by 6, 4 by 5),
                    )
            }

            @Nested
            inner class Bounded {
                @Test
                fun `Enforcing order`() =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 2 by 5
                            override val upperBoundExclusive: Int2D = 5 by 7
                        },
                        true,
                        listOf(4 by 5, 3 by 6, 2 by 6),
                    )

                @Test
                fun `Not enforcing order`() =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = 2 by 5
                            override val upperBoundExclusive: Int2D = 5 by 7
                        },
                        false,
                        listOf(2 by 6, 3 by 6, 4 by 5),
                    )
            }

            private fun check(
                viewport: Viewport,
                enforceOrder: Boolean,
                expected: List<Int2D>,
            ) = check(7 by 4, -1 by 7, viewport, enforceOrder, expected)
        }

        @Nested
        inner class FromNorthWestToSouthEast {
            @Nested
            inner class Unbounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        Viewport.Unbounded,
                        enforceOrder,
                        listOf(-1 by 2, -1 by 3, 0 by 4, 0 by 5, 1 by 6, 1 by 7, 2 by 8, 2 by 9, 3 by 10),
                    )
            }

            @Nested
            inner class LowerBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 1 by 0
                        },
                        enforceOrder,
                        listOf(1 by 6, 1 by 7, 2 by 8, 2 by 9, 3 by 10),
                    )
            }

            @Nested
            inner class UpperBounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 1 by 7
                        },
                        enforceOrder,
                        listOf(-1 by 2, -1 by 3, 0 by 4, 0 by 5),
                    )
            }

            @Nested
            inner class Bounded {
                @Test
                fun `Enforcing order`() = check(true)

                @Test
                fun `Not enforcing order`() = check(false)

                private fun check(enforceOrder: Boolean) =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = -1 by 3
                            override val upperBoundExclusive: Int2D = 2 by 7
                        },
                        enforceOrder,
                        listOf(-1 by 3, 0 by 4, 0 by 5, 1 by 6),
                    )
            }

            private fun check(
                viewport: Viewport,
                enforceOrder: Boolean,
                expected: List<Int2D>,
            ) = check(-1 by 2, 3 by 10, viewport, enforceOrder, expected)
        }

        @Nested
        inner class FromSouthEastToNorthWest {
            @Nested
            inner class Unbounded {
                @Test
                fun `Enforcing order`() =
                    check(
                        Viewport.Unbounded,
                        true,
                        listOf(3 by 10, 2 by 9, 2 by 8, 1 by 7, 1 by 6, 0 by 5, 0 by 4, -1 by 3, -1 by 2),
                    )

                @Test
                fun `Not enforcing order`() =
                    check(
                        Viewport.Unbounded,
                        false,
                        listOf(-1 by 2, -1 by 3, 0 by 4, 0 by 5, 1 by 6, 1 by 7, 2 by 8, 2 by 9, 3 by 10),
                    )
            }

            @Nested
            inner class LowerBounded {
                @Test
                fun `Enforcing order`() =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 1 by 7
                        },
                        true,
                        listOf(3 by 10, 2 by 9, 2 by 8, 1 by 7),
                    )

                @Test
                fun `Not enforcing order`() =
                    check(
                        object : Viewport.LowerBounded {
                            override val lowerBoundInclusive: Int2D = 1 by 7
                        },
                        false,
                        listOf(1 by 7, 2 by 8, 2 by 9, 3 by 10),
                    )
            }

            @Nested
            inner class UpperBounded {
                @Test
                fun `Enforcing order`() =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 2 by 7
                        },
                        true,
                        listOf(1 by 6, 0 by 5, 0 by 4, -1 by 3, -1 by 2),
                    )

                @Test
                fun `Not enforcing order`() =
                    check(
                        object : Viewport.UpperBounded {
                            override val upperBoundExclusive: Int2D = 2 by 7
                        },
                        false,
                        listOf(-1 by 2, -1 by 3, 0 by 4, 0 by 5, 1 by 6),
                    )
            }

            @Nested
            inner class Bounded {
                @Test
                fun `Enforcing order`() =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = -1 by 3
                            override val upperBoundExclusive: Int2D = 2 by 7
                        },
                        true,
                        listOf(1 by 6, 0 by 5, 0 by 4, -1 by 3),
                    )

                @Test
                fun `Not enforcing order`() =
                    check(
                        object : Viewport.Bounded {
                            override val lowerBoundInclusive: Int2D = -1 by 3
                            override val upperBoundExclusive: Int2D = 2 by 7
                        },
                        false,
                        listOf(-1 by 3, 0 by 4, 0 by 5, 1 by 6),
                    )
            }

            private fun check(
                viewport: Viewport,
                enforceOrder: Boolean,
                expected: List<Int2D>,
            ) = check(3 by 10, -1 by 2, viewport, enforceOrder, expected)
        }
    }

    private fun check(
        first: Int2D,
        last: Int2D,
        viewport: Viewport,
        enforceOrder: Boolean,
        expected: List<Int2D>,
    ) {
        val actual = BresenhamLine(first, last, viewport, enforceOrder)
        // Assert two times to check the independence of iterators
        assertEquals(expected, actual.toList())
        assertEquals(expected, actual.toList())
    }
}
