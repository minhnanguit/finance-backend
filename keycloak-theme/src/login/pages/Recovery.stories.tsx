import type { Meta, StoryObj } from "@storybook/react";
import { createKcPageStory } from "../KcPageStory";

const { KcPageStory } = createKcPageStory({ pageId: "login-reset-password.ftl" });

const meta = { title: "Finance/Recovery/Reset password", component: KcPageStory } satisfies Meta<typeof KcPageStory>;
export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = {
    render: () => <KcPageStory kcContext={{ locale: { currentLanguageTag: "vi" } }} />
};
export const English: Story = { render: () => <KcPageStory /> };
