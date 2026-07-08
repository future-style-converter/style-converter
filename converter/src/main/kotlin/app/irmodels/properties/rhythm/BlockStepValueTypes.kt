package app.irmodels.properties.rhythm

import kotlinx.serialization.Serializable

@Serializable
enum class BlockStepAlignValue { AUTO, CENTER, START, END }

@Serializable
enum class BlockStepInsertValue { MARGIN, PADDING }

@Serializable
enum class BlockStepRoundValue { UP, DOWN, NEAREST }
