import type { Meta, StoryObj } from "@storybook/react";
import { createKcPageStory } from "../KcPageStory";

const { KcPageStory } = createKcPageStory({ pageId: "register.ftl" });

const meta = { title: "Finance/Register", component: KcPageStory } satisfies Meta<typeof KcPageStory>;
export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = { render: () => <KcPageStory /> };
export const Vietnamese: Story = {
    render: () => <KcPageStory kcContext={{ locale: { currentLanguageTag: "vi" } }} />
};
