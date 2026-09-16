export interface CatalogItem {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  category: string | null;
  unitPrice: number;
  vendorId: number;
  vendorName: string;
}
