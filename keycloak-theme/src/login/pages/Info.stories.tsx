import type { Meta, StoryObj } from "@storybook/react";
import { createKcPageStory } from "../KcPageStory";

const { KcPageStory } = createKcPageStory({ pageId: "info.ftl" });
const meta = { title: "Finance/Status/Info", component: KcPageStory } satisfies Meta<typeof KcPageStory>;
export default meta;
type Story = StoryObj<typeof meta>;
export const Default: Story = {
    render: () => (
        <KcPageStory
            kcContext={{
                locale: { currentLanguageTag: "vi" },
                messageHeader: "Yêu cầu đã được xử lý",
                message: { type: "success", summary: "Bạn có thể tiếp tục sử dụng ứng dụng Finance." }
            }}
        />
    )
};
