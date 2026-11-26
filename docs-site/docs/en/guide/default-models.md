# Default Models

In "Settings → Default Models", besides the default model used for new sessions, you can also assign a model individually to several specific purposes.

## Vision Model

When the current chat model supports vision, images are attached directly to the current conversation and analyzed by the chat model; only when the chat model does not support vision is the configuration here used to create a separate vision session.

-   **Follow the current chat model** (default): the separate vision session also uses the current chat model, so make sure the model supports image input.
-   **Assign a dedicated model**: the separate vision session uses the assigned model, suitable for when the chat model does not support images. The configured provider must be enabled and have an API Key filled in, otherwise it falls back to the current chat model.

Each model in the list is annotated with whether it supports image input, for your reference. Note that there is **no forced blocking** here: if you pick a model that does not support images, vision will fail, the AI will receive the error message and handle it itself (it will usually prompt you to switch to another model).

A few things worth knowing about using vision:

-   The AI can pass in up to **5 images** at a time for comparative analysis.
-   When you attach images directly, just keep asking follow-up questions in the current chat; a separate vision session generates a session identifier, and the AI can continue asking questions in that session, retaining the images and Q&A it has already seen.
-   The default clarity and original-image-priority modes both prefer to keep original JPEG, PNG, WebP, and GIF files no larger than **4 MiB**; other images are converted to JPEG with a longest edge of no more than **1536 pixels**.
-   Low-clarity mode converts images to JPEG with a longest edge of no more than **512 pixels**, suitable for saving tokens.

## Image Generation Model

Sets the model used by the `generateImage` tool, supporting OpenAI- and Gemini-compatible interfaces. The image-editing tool `editImage` (image-to-image) also uses this configuration.

-   The OpenAI-compatible interface supports parameters such as size, count, quality, background, style, and output format.
-   The Gemini-compatible interface uses the model's default size and resolution; when generating multiple images it calls multiple times.
-   Images are saved by default to `~/.aiverse/generated-images/`, and you can also specify a workspace path. The limit is 20MB per image and 48MB total per call.

The image-editing tool `editImage` reworks workspace reference images according to an instruction: the first image is the base, the rest are references, up to 4 in total. **Reference images are uploaded to the image service**, so avoid using it on images containing sensitive information. It uses OpenAI's `/images/edits` endpoint or Gemini's reference-image channel, and supports parameters such as size, count, and quality (consistent with `generateImage`); if the current image generation model does not support editing (such as dall-e-3), you will be prompted to switch to a model that supports image editing.

## Image-Model In-Conversation Image Generation

After connecting a chat model that supports image output through the Gemini-compatible interface, you can generate images directly, upload reference images, and keep modifying the generated results in subsequent conversation.

## Subagent Model Tiers

When the AI dispatches a subagent, it picks a tier according to the task's difficulty rather than always using the same model. Here you define "which tier maps to which model":

Tier

Suited for

Lightweight

Read-only exploration, bulk locating, mechanical renaming

Balanced

Single-file edits, routine bug fixes

Powerful

Cross-module refactoring, tricky troubleshooting, solution design

-   Each tier can be left as "Not configured": **just configure the tiers you will actually use**. Unconfigured tiers are downgraded automatically (see below).
-   Each tier can be assigned any model under any enabled provider, with search by model name supported — even with hundreds of models behind a relay station you can find the right one.
-   Priority: when a subagent definition hard-codes a model, the definition takes precedence (the tier is ignored); otherwise the tier chosen by the AI is used.
-   Downgrade order: if the requested tier is not configured, or its provider has been deleted/disabled → fall back to the "Balanced" tier → if that is still not configured, inherit the current session's model. When a downgrade happens, the AI receives a specific explanation in the tool result, and you can see in the conversation which model it continues with.
-   When you delete a provider, the tiers referencing it are cleared automatically, leaving no configuration pointing at an empty model.

## Title Summary Model

After a new session sends its first message, AiVerse automatically generates a title for the session. Here you can assign a model dedicated to that task.

-   **Follow the current chat model** (default).
-   **Assign a dedicated model**: pick a cheap, fast, small model — a task like title generation doesn't need a good model.

Title generation runs asynchronously in the background and won't slow down your conversation. If generation fails or no result can be obtained, the temporary title produced by truncating the first sentence is kept, without affecting use.

The prompt used to generate titles can also be modified yourself; see "Custom Prompts" for how.

## Input Refinement Model

The "Refine input" shortcut button hands what you have already written in the input box to a model to polish and fills it back. Here you can assign a model dedicated to refinement (**from 1.0.3**):

-   **Follow the current chat model** (default): refinement just uses the model selected for the current session.
-   **Assign a dedicated model**: the refinement request is temporarily switched to that dedicated model to send, then restored afterward, without affecting the model selected for the current session; it is a good fit for picking a cheap, fast model to do the polishing.

When no usable model is configured, tapping "Refine input" prompts you to first add and configure a model under "AI Providers".

## Compaction Model

When the context approaches the threshold and triggers automatic compression, it uses the current chat model to send the summary request by default. Here you can assign a model dedicated to compression:

-   **Follow the current chat model** (default): compression just uses the model selected for the current session.
-   **Assign a dedicated model**: the compression round is temporarily switched to that dedicated model to send the summary request, then restored to the chat model afterward, without affecting the model selected for the current session; it is a good fit for picking a cheap or long-window model to do the compression.

When the compression model's window is small it processes in chunks, which may increase waiting time and call cost; see [Context Usage and Compression](https://aivapp.cn/en/guide/chat.md).
