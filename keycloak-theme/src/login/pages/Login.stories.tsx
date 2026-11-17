import type { Meta, StoryObj } from "@storybook/react";
import { createKcPageStory } from "../KcPageStory";

const { KcPageStory } = createKcPageStory({ pageId: "login.ftl" });

const meta = { title: "Finance/Login", component: KcPageStory } satisfies Meta<typeof KcPageStory>;
export default meta;
type Story = StoryObj<typeof meta>;

export const Default: Story = { render: () => <KcPageStory /> };
export const Vietnamese: Story = {
    render: () => <KcPageStory kcContext={{ locale: { currentLanguageTag: "vi" } }} />
};
export const InvalidCredentials: Story = {
    render: () => (
        <KcPageStory
            kcContext={{
                login: { username: "user@example.com" },
                messagesPerField: {
                    existsError: (field: string, ...fields: string[]) => [field, ...fields].some(name => ["username", "password"].includes(name)),
                    get: (field: string) => (["username", "password"].includes(field) ? "Email hoặc mật khẩu không đúng." : "")
                }
            }}
        />
    )
};
