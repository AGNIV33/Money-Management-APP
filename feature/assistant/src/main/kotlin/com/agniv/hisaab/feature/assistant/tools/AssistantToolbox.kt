package com.agniv.hisaab.feature.assistant.tools

import com.agniv.hisaab.core.common.money.Money
import com.agniv.hisaab.core.common.money.sum
import com.agniv.hisaab.core.domain.loan.EmiMath
import com.agniv.hisaab.core.domain.loan.LoanTerms
import com.agniv.hisaab.core.domain.model.Category
import com.agniv.hisaab.core.domain.model.Expense
import com.agniv.hisaab.core.domain.model.UdhaarDirection
import com.agniv.hisaab.core.domain.usecase.GoalsOverview
import com.agniv.hisaab.core.domain.usecase.LoansOverview
import com.agniv.hisaab.core.domain.usecase.UdhaarOverview
import com.agniv.hisaab.core.ml.insight.Insight
import com.agniv.hisaab.core.ml.insight.InsightEngine
import com.agniv.hisaab.core.ml.insight.InsightInput
import com.agniv.hisaab.core.ml.report.ReportPayloadBuilder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Everything a tool may look at, already filtered to what the signed-in person is allowed to see
 * (PRIVATE items of other members are not in here at all).
 */
data class ToolContext(
    val familyId: String,
    val today: LocalDate,
    val expenses: List<Expense>,
    val categories: List<Category>,
    val budget: Money?,
    val loans: LoansOverview,
    val udhaar: UdhaarOverview,
    val goals: GoalsOverview,
)

/**
 * The answers the voice assistant can ask the phone for (IMPLEMENTATION_PLAN 4.12). Every answer is a small
 * JSON object of TOTALS in whole rupees. There is no field that can hold a person's name, a note, a phone or
 * an account number, so even a tricked assistant cannot read them out. [isSafe] checks that again before an
 * answer leaves the phone.
 */
@Suppress("TooManyFunctions") // one small function per tool
object AssistantToolbox {
    const val FINANCE_SNAPSHOT = "finance_snapshot"
    const val LOANS_SUMMARY = "loans_summary"
    const val UDHAAR_SUMMARY = "udhaar_summary"
    const val GOALS_SUMMARY = "goals_summary"
    const val EMI_OPTIONS = "emi_options"
    const val AFFORDABILITY = "affordability"
    const val WEB_PRICE_LOOKUP = "web_price_lookup"

    private const val TOP_CATEGORIES = 5
    private const val PAST_MONTHS = 3L
    private const val PERCENT = 100
    private const val DEFAULT_RATE_PERCENT = 12.0
    private const val MAX_RATE_PERCENT = 42.0
    private const val BPS_PER_PERCENT = 100
    private const val MAX_PRICE_RUPEES = 1_000_000_000.0

    // The month choices a person would ask about; they are the content of the answer, not tuning numbers.
    @Suppress("MagicNumber")
    private val TENURES = listOf(3, 6, 9, 12, 18, 24, 36)

    @Suppress("MagicNumber")
    private val SAVING_PLANS = listOf(6, 12, 24)
    private const val LONGEST_SAFE_TEXT = 40

    /** Names the phone itself can answer. [WEB_PRICE_LOOKUP] goes to the server and is handled by the runner. */
    val localTools =
        setOf(FINANCE_SNAPSHOT, LOANS_SUMMARY, UDHAAR_SUMMARY, GOALS_SUMMARY, EMI_OPTIONS, AFFORDABILITY)

    fun run(
        name: String,
        args: JsonObject,
        ctx: ToolContext,
    ): JsonObject =
        when (name) {
            FINANCE_SNAPSHOT -> financeSnapshot(ctx)
            LOANS_SUMMARY -> loansSummary(ctx)
            UDHAAR_SUMMARY -> udhaarSummary(ctx)
            GOALS_SUMMARY -> goalsSummary(ctx)
            EMI_OPTIONS -> emiOptions(args)
            AFFORDABILITY -> affordability(args, ctx)
            else -> error("unknown_tool")
        }

    fun error(code: String): JsonObject = buildJsonObject { put("error", code) }

    private fun rupees(money: Money): Long = money.rupeesPart

    private fun financeSnapshot(ctx: ToolContext): JsonObject {
        val month = YearMonth.from(ctx.today)
        val request =
            ReportPayloadBuilder.build(ctx.familyId, month, "en", ctx.expenses, ctx.categories, ctx.budget, ctx.loans.monthlyEmi)
        val insights = InsightEngine.compute(InsightInput(ctx.today, ctx.expenses, ctx.budget, ctx.loans.monthlyEmi, emptyList()))
        val forecast = insights.filterIsInstance<Insight.MonthEndForecast>().firstOrNull()
        val spent = Money(request.totalPaise)
        return buildJsonObject {
            put("month", month.toString())
            put("spentThisMonthRupees", rupees(spent))
            put("spentLastMonthRupees", rupees(Money(request.previousTotalPaise)))
            put("daysLeftInMonth", ChronoUnit.DAYS.between(ctx.today, month.atEndOfMonth()))
            ctx.budget?.let {
                put("budgetRupees", rupees(it))
                put("budgetLeftRupees", rupees(Money(maxOf(0L, (it - spent).paise))))
                put("overBudget", spent > it)
            }
            forecast?.let { put("projectedMonthEndRupees", rupees(it.projected)) }
            request.emiSharePercent?.let { put("emiPercentOfThisMonthSpending", it) }
            put(
                "topCategories",
                buildJsonArray {
                    request.categories
                        .filter { it.thisPaise > 0 }
                        .take(TOP_CATEGORIES)
                        .forEach { c ->
                            add(
                                buildJsonObject {
                                    put("name", c.name)
                                    put("rupees", rupees(Money(c.thisPaise)))
                                },
                            )
                        }
                },
            )
        }
    }

    private fun loansSummary(ctx: ToolContext): JsonObject {
        val open = ctx.loans.items.filter { !it.progress.isClosed }
        val next = open.filter { it.progress.next != null }.minByOrNull { it.progress.next?.date ?: LocalDate.MAX }
        return buildJsonObject {
            put("openLoans", open.size)
            put("monthlyEmiRupees", rupees(ctx.loans.monthlyEmi))
            put("totalStillToPayRupees", rupees(ctx.loans.outstanding))
            put("overdueEmis", open.sumOf { it.progress.overdueCount })
            next?.let { item ->
                put(
                    "nextEmi",
                    buildJsonObject {
                        put("inDays", item.progress.next?.daysLeft ?: 0L)
                        put("amountRupees", rupees(item.loan.emi))
                        put("loanType", item.loan.type.name)
                    },
                )
            }
        }
    }

    private fun udhaarSummary(ctx: ToolContext): JsonObject {
        val open = ctx.udhaar.items.filter { !it.settled }
        return buildJsonObject {
            put("youWillGetRupees", rupees(ctx.udhaar.youWillGet))
            put("youWillGiveRupees", rupees(ctx.udhaar.youWillGive))
            put("openEntriesToGet", open.count { it.udhaar.direction == UdhaarDirection.THEY_OWE_ME })
            put("openEntriesToGive", open.count { it.udhaar.direction == UdhaarDirection.I_OWE })
            put("overdueEntries", open.count { it.overdue })
        }
    }

    /** Goals are described by progress only: their names are typed by the family and are never sent. */
    private fun goalsSummary(ctx: ToolContext): JsonObject =
        buildJsonObject {
            put("goalCount", ctx.goals.items.size)
            put("totalSavedRupees", rupees(ctx.goals.totalSaved))
            put("totalTargetRupees", rupees(ctx.goals.totalTarget))
            put(
                "goals",
                buildJsonArray {
                    ctx.goals.items.forEach { g ->
                        add(
                            buildJsonObject {
                                put("progressPercent", (g.fraction * PERCENT).toInt())
                                put("reached", g.reached)
                                g.monthsLeft?.let { put("monthsLeft", it) }
                                g.perMonthNeeded?.let { put("needsPerMonthRupees", rupees(it)) }
                            },
                        )
                    }
                },
            )
        }

    private fun JsonObject.number(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun priceFrom(args: JsonObject): Long? =
        args
            .number("price_rupees")
            ?.takeIf { it >= 1.0 && it <= MAX_PRICE_RUPEES }
            ?.toLong()

    private fun emiOptions(args: JsonObject): JsonObject {
        val price = priceFrom(args)
        val down = (args.number("down_payment_rupees") ?: 0.0).coerceAtLeast(0.0).toLong()
        return when {
            price == null -> error("invalid_amount")
            price - down < 1 -> error("down_payment_covers_price")
            else -> emiTable(price, down, args.number("annual_rate_percent"))
        }
    }

    private fun emiTable(
        price: Long,
        down: Long,
        given: Double?,
    ): JsonObject {
        val loan = price - down
        val rate = (given ?: DEFAULT_RATE_PERCENT).coerceIn(0.0, MAX_RATE_PERCENT)
        val bps = Math.round(rate * BPS_PER_PERCENT).toInt()
        return buildJsonObject {
            put("priceRupees", price)
            put("downPaymentRupees", down)
            put("loanAmountRupees", loan)
            put("annualRatePercent", rate)
            put("rateWasAssumed", given == null)
            put(
                "options",
                buildJsonArray {
                    TENURES.forEach { months ->
                        val summary = EmiMath.summary(LoanTerms(Money.ofRupees(loan), bps, months))
                        add(
                            buildJsonObject {
                                put("months", months)
                                put("emiRupees", rupees(summary.emi))
                                put("totalInterestRupees", rupees(summary.totalInterest))
                                put("totalPayableRupees", rupees(summary.totalPayable))
                            },
                        )
                    }
                },
            )
        }
    }

    /** What the family spends in a normal month: the average of the last full months that have entries. */
    private fun normalMonthlySpend(ctx: ToolContext): Money {
        val thisMonth = YearMonth.from(ctx.today)
        val byMonth =
            ctx.expenses
                .filter { !it.deleted }
                .groupBy { YearMonth.from(it.date) }
        val past =
            (1..PAST_MONTHS)
                .mapNotNull { byMonth[thisMonth.minusMonths(it)]?.map { e -> e.amount }?.sum() }
                .filter { it.paise > 0 }
        val fallback = byMonth[thisMonth]?.map { it.amount }?.sum() ?: Money.ZERO
        return if (past.isEmpty()) fallback else Money(past.map { it.paise }.average().toLong())
    }

    private fun affordability(
        args: JsonObject,
        ctx: ToolContext,
    ): JsonObject {
        val price = priceFrom(args) ?: return error("invalid_amount")
        val normal = normalMonthlySpend(ctx)
        val thisMonth =
            ctx.expenses
                .filter { !it.deleted && YearMonth.from(it.date) == YearMonth.from(ctx.today) }
                .map { it.amount }
                .sum()
        val goalsNeed =
            ctx.goals.items
                .filter { !it.reached }
                .mapNotNull { it.perMonthNeeded }
                .sum()
        return buildJsonObject {
            put("priceRupees", price)
            put("normalMonthlySpendRupees", rupees(normal))
            if (normal.paise > 0) put("priceIsPercentOfNormalMonthlySpend", price * PERCENT * Money.PAISE_PER_RUPEE / normal.paise)
            put("existingMonthlyEmiRupees", rupees(ctx.loans.monthlyEmi))
            if (normal.paise > 0 && ctx.loans.monthlyEmi.paise > 0) {
                put("existingEmiIsPercentOfNormalSpend", ctx.loans.monthlyEmi.paise * PERCENT / normal.paise)
            }
            put("savingsGoalsNeedPerMonthRupees", rupees(goalsNeed))
            ctx.budget?.let { put("budgetLeftThisMonthRupees", rupees(Money(maxOf(0L, (it - thisMonth).paise)))) }
            put(
                "saveEveryMonthToBuyOutright",
                buildJsonArray {
                    SAVING_PLANS.forEach { months ->
                        add(
                            buildJsonObject {
                                put("inMonths", months)
                                put("perMonthRupees", (price + months - 1) / months)
                            },
                        )
                    }
                },
            )
            put("incomeKnown", false)
        }
    }

    /**
     * True when every text in [answer] is short and plain (letters, digits, spaces, a few symbols) with no long digit
     * run. The phone checks this before it hands any answer to the assistant, as a last line of defence.
     */
    fun isSafe(answer: JsonElement): Boolean =
        when (answer) {
            is JsonObject -> answer.values.all(::isSafe)
            is JsonArray -> answer.all(::isSafe)
            is JsonNull -> true
            is JsonPrimitive -> !answer.isString || isSafeText(answer.content)
        }

    private val plainText = Regex("^[A-Za-z0-9 _:.\\-]{0,$LONGEST_SAFE_TEXT}$")
    private val longDigits = Regex("\\d{9,}")

    private fun isSafeText(text: String): Boolean = plainText.matches(text) && !longDigits.containsMatchIn(text)
}
