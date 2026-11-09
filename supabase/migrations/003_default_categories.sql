-- Built-in categories (PRD F4). IDs match core/database/.../seed/DefaultCategories.kt.
alter table public.categories add column if not exists sort_order int not null default 0;

insert into public.categories (id, family_id, name, name_hi, icon, color, is_default, sort_order) values
  ('a156efbe-b53f-3019-811f-30c2d8567361', null, 'Groceries', 'किराना', 'shopping_basket', 4281236786, true, 0),
  ('d19126f3-f337-30a4-8d9a-d09cb866b1ce', null, 'Milk', 'दूध', 'water_drop', 4284246976, true, 1),
  ('b2311670-4075-38d8-b479-d50ba8072937', null, 'Vegetables', 'सब्ज़ी', 'eco', 4283796271, true, 2),
  ('09d95305-98fa-3840-bed6-ca31c563d4a2', null, 'Rent', 'किराया', 'home', 4285353025, true, 3),
  ('4e17013d-9ff1-3369-b3fe-08fd5ed751be', null, 'Electricity', 'बिजली', 'bolt', 4294551589, true, 4),
  ('5545c8b3-ccf1-37e8-80ee-89f7d6cdf84a', null, 'Water', 'पानी', 'water', 4278356177, true, 5),
  ('1d1dce8a-b25a-3aaa-b2a5-20284a162bca', null, 'Gas/LPG', 'गैस', 'local_fire_department', 4293284096, true, 6),
  ('70491a9d-d1ba-3792-9c4b-176615b61a10', null, 'Mobile/Internet', 'मोबाइल/इंटरनेट', 'smartphone', 4278225275, true, 7),
  ('2dda8171-5e37-3801-b92a-a291f563b1d4', null, 'Maid/Help', 'घरेलू सहायक', 'cleaning_services', 4287458915, true, 8),
  ('bd611778-3664-329d-90ac-2850ffbd2421', null, 'Fuel', 'पेट्रोल/डीज़ल', 'local_gas_station', 4291176488, true, 9),
  ('3088bf49-2684-3ae2-ac1d-748e19fc809a', null, 'Transport', 'आना-जाना', 'directions_bus', 4281944491, true, 10),
  ('31586a39-badd-3178-8dc2-0739c0a9061f', null, 'Food Outside', 'बाहर खाना', 'restaurant', 4292363029, true, 11),
  ('272f87e8-507e-3efd-b5c6-ff986778cecc', null, 'Shopping', 'शॉपिंग', 'shopping_bag', 4289533015, true, 12),
  ('e3949412-5e91-3a6f-94aa-8bafee967587', null, 'Medical', 'दवा/इलाज', 'medical_services', 4292030255, true, 13),
  ('ba5f51b2-e0ff-3e2a-b5d6-66cd89e40a2b', null, 'Education', 'पढ़ाई', 'school', 4279592384, true, 14),
  ('6a6ba212-0cc2-3d67-98e2-e8a6dc2dc39d', null, 'Festivals/Gifts', 'त्योहार/उपहार', 'redeem', 4293880832, true, 15),
  ('d494f1d1-1f6a-3fbc-9cd0-b9ac0dadfdf2', null, 'EMI', 'ईएमआई', 'account_balance', 4282281944, true, 16),
  ('922cec38-9309-33de-a7f9-eded83611a8d', null, 'Entertainment', 'मनोरंजन', 'movie', 4286259106, true, 17),
  ('752c4d87-7774-3f88-a95a-8287d619fb7e', null, 'Others', 'अन्य', 'category', 4283723386, true, 18)
on conflict (id) do update set name = excluded.name, name_hi = excluded.name_hi, icon = excluded.icon,
  color = excluded.color, sort_order = excluded.sort_order;
