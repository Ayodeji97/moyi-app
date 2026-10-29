package com.moyi.app.feature.onboarding.common

import com.moyi.app.domain.bond.BondType
import com.moyi.app.feature.onboarding.resources.Res
import com.moyi.app.feature.onboarding.resources.bond_type_couple
import com.moyi.app.feature.onboarding.resources.bond_type_family
import com.moyi.app.feature.onboarding.resources.bond_type_friends
import com.moyi.app.feature.onboarding.resources.join_confirm_line_couple
import com.moyi.app.feature.onboarding.resources.join_confirm_line_family
import com.moyi.app.feature.onboarding.resources.join_confirm_line_friends
import org.jetbrains.compose.resources.StringResource

/**
 * The three relationship-type cards (states.md §1a), in their drawn order: a
 * UI model over the domain's [BondType], which it maps exactly ([bondType]).
 */
enum class RelationshipType(
    val label: StringResource,
    val confirmLine: StringResource,
    val bondType: BondType,
) {
    Couple(Res.string.bond_type_couple, Res.string.join_confirm_line_couple, BondType.COUPLE),
    Friends(Res.string.bond_type_friends, Res.string.join_confirm_line_friends, BondType.FRIENDS),
    Family(Res.string.bond_type_family, Res.string.join_confirm_line_family, BondType.FAMILY),
    ;

    companion object {
        /**
         * The card a bond's type reads as, for the joiner's confirm line.
         * `PARENT_CHILD` has no card; it reads as [Family], whose prompt overlay
         * covers parent, child, sibling and cousin (states.md §1a).
         */
        fun of(type: BondType): RelationshipType = when (type) {
            BondType.COUPLE -> Couple
            BondType.FRIENDS -> Friends
            BondType.FAMILY, BondType.PARENT_CHILD -> Family
        }
    }
}
