# Token Stats and Costs

"Settings → Token Stats" records the consumption of every AI call, letting you view token usage, costs, and cache hit rates.

Data is recorded from when this feature went live; earlier historical calls have no details. The page refreshes in real time.

## Period Switching and Dimension Filters

Four tabs at the top: Today / Last 7 days / Last 30 days / All. Switching updates all the cards, charts, and lists below together. The trend chart for "Today" is shown by hour, the rest by day.

Below the period is a filter bar for dimensions:

-   **Provider filter**: a dropdown to choose "All providers" or a specific provider.
-   **Model filter**: a dropdown to choose "All models" or a specific model.
-   **Clear filter**: appears once a filter is active; tapping it resets everything back to all data in one tap.
-   In addition, directly tapping any row in "Provider stats" or "Model stats" also quickly sets that item as a filter condition, and tapping it again cancels the filter.

## Overview

Five cards: total consumption (input plus output), request count, cost, cache hit rate, and launch count (**from 1.0.3**, the last row takes up a full-width card). The first four are tallied by the selected period and filter; **launch count** is the cumulative number of times the App entered the foreground (both cold starts and resuming from the background / recent tasks count as one), and is **unrelated to model calls** — it is unaffected by the period and provider/model filters, and shows the real cumulative value even when there hasn't been a single LLM call.

The cost is an **estimate**. It prefers the custom unit prices you filled in under "Provider → Model", and only falls back to the public prices from models.dev for those left blank: cache reads are priced at the cache price, or estimated at 10% of the input price when there is no cache price; cache writes are priced at the cache-write price, or estimated at 1.25x the input price when there is none. The total cost applies unit prices separately per "channel + model" and then sums them, so the same model called through different providers is priced separately. The cache hit rate is cache reads divided by total input, and the card also lists this period's cache read and write token volumes below it.

Costs Are for Reference Only

This is an estimate based on public unit prices, not a bill. If a relay has changed its prices, offers discounts, or the model you use has no price data, the estimate will differ from the actual charge. Defer to the bill in the provider's dashboard.

## Consumption Trend

A line chart shows three lines within the period — input, output, and cache hit (blue for input, orange for output, purple for cache) — with a translucent fill under the lines to make the overlapping parts easier to distinguish. The left axis is the token count (abbreviated with k / M).

The time axis is fully expanded across the period, filling in 0 for days or hours with no records, so the trend is still readable even when calls are concentrated on a single day.

## Channel and Model Stats

**Channel stats** aggregates by provider, showing the channel name, the number of configured key credentials, the success-rate percentage with a horizontal progress bar, and highlighting the request count and token consumption on the right, sorted by total consumption in descending order.

**Model stats** aggregates by model, with each row showing the model name, input/output consumption, call count, and success rate, sorted by total consumption in descending order.

Tapping any channel or model row quickly applies it as a filter that drives the data across the whole page.

## Call Records

A detail table of 10 entries per page with paging at the bottom; swipe left and right to see all fields:

Field

Description

Call time

Year, month, day, hour, minute

Channel / model

Which provider and model were used

Thinking effort

The tier for this call; models without tiers show `-`

Actually sent

The thinking parameter actually sent to the server (e.g. `effort=max`, `thinking=disabled`); when a downgrade happens it shows "original tier → actually sent", so you can tell whether the setting was honored by the server

Status

success / error / cancelled

Input / output / cache read / cache write

The token counts of each kind

Cache hit rate

Cache input divided by total input

First-token latency

Time from when the request was sent until the first content event (text, thinking, or tool-call result) arrived

Total duration

Total length of this call

Estimated cost

Models without price data show `-`

The turn containing only tool calls also has its first-token latency counted.

## Context Usage (from 1.0.0)

The chat page's input box has a **context usage ring** around it, reflecting how much of the model's available input budget the current session occupies. **From 1.0.0**:

-   **The usage ring shows a percentage**: the center directly shows the percentage used (formatted as a percentage, without scientific notation).
-   **Usage details**: tap the usage ring to view the breakdown, with a single-line display at the top showing "used / total", and below it each component (system prompt, tool definitions, conversation content, etc.) listed with its usage and share.

The ring's total budget comes from the model's **context window**. If a provider's API only returns a per-request input window (such as `max_input_tokens` / `inputTokenLimit`) and no total-window field, the budget falls back to the built-in catalog or the default value rather than mistaking the input window for the total window, so the percentage isn't inflated.

## Reset

The trash icon in the top-right of the page clears all call records, and the stats reset to zero accordingly. A confirmation dialog pops up — **once cleared, it cannot be recovered**.
