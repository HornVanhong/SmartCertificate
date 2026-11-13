import {
  Activity,
  Award,
  Building2,
  FileCheck2,
  FilePlus2,
  FolderKanban,
  LayoutDashboard,
  Palette,
  PenTool,
  Settings,
  ShieldAlert,
  Sparkles,
  Upload,
  Users,
  type LucideIcon,
} from "lucide-react";

export interface NavChildItem {
  label: string;
  href: string;
  icon: LucideIcon;
}

export interface NavItem {
  label: string;
  href: string;
  icon: LucideIcon;
  children?: NavChildItem[];
}

export const NAVIGATION_ITEMS: NavItem[] = [
  {
    label: "Dashboard",
    href: "/",
    icon: LayoutDashboard,
  },
  {
    label: "Certificate Studio",
    href: "/studio",
    icon: Sparkles,
    children: [
      { label: "Template Gallery", href: "/studio/templates", icon: FolderKanban },
      { label: "My Designs", href: "/studio/designs", icon: PenTool },
      { label: "New Certificate", href: "/studio/new", icon: FilePlus2 },
    ],
  },
  {
    label: "Certificates",
    href: "/certificates",
    icon: Award,
    children: [
      { label: "All Certificates", href: "/certificates", icon: FileCheck2 },
      { label: "Bulk Generation", href: "/certificates/bulk", icon: Upload },
      { label: "Revoked", href: "/certificates/revoked", icon: ShieldAlert },
    ],
  },
  {
    label: "Organization",
    href: "/organization",
    icon: Building2,
    children: [
      { label: "Profile", href: "/organization", icon: Building2 },
      { label: "Branding & Logo", href: "/organization/branding", icon: Palette },
      { label: "Signatures", href: "/organization/signatures", icon: PenTool },
    ],
  },
  {
    label: "Users & Roles",
    href: "/users",
    icon: Users,
  },
  {
    label: "Activity Logs",
    href: "/activity-logs",
    icon: Activity,
  },
  {
    label: "Settings",
    href: "/settings",
    icon: Settings,
  },
];
