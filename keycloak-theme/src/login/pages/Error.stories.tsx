import type { Meta, StoryObj } from "@storybook/react";
import { createKcPageStory } from "../KcPageStory";

const { KcPageStory } = createKcPageStory({ pageId: "error.ftl" });
const meta = { title: "Finance/Status/Error", component: KcPageStory } satisfies Meta<typeof KcPageStory>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Default: Story = {
    render: () => (
        <KcPageStory
            kcContext={{
                locale: { currentLanguageTag: "vi" },
                message: { type: "error", summary: "Không thể hoàn thành yêu cầu. Vui lòng thử lại." }
            }}
        />
    )
};
