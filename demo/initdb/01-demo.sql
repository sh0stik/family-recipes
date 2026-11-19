-- Demo data for the local `docker compose` stack: schema + 7 English recipes.
--
-- Loaded once by the postgres container on its first start (docker-entrypoint-initdb.d), i.e.
-- only while the `postgres-data` volume is empty. `docker compose down -v` resets it.
--
-- Demo login (localhost only): username `demo`, password `demo-password-123`.
-- The password is stored only as a bcrypt hash below.
-- The `family` account owns the (empty) shared family collection and has no usable password.
--
-- Recipe photos live in demo/uploads (credits: demo/CREDITS.md).
-- Produced with pg_dump --no-owner --no-privileges from a local throwaway database; the schema
-- is the one Hibernate generates, and the backend keeps it current with ddl-auto=update.

--
-- PostgreSQL database dump
--


-- Dumped from database version 17.11 (Homebrew)
-- Dumped by pg_dump version 18.7 (Homebrew)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: blacklisted_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.blacklisted_tokens (
    expiry_date timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    token character varying(512) NOT NULL
);


--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categories (
    id uuid NOT NULL,
    parent_category_id uuid,
    name character varying(255) NOT NULL
);


--
-- Name: content_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.content_reports (
    created_at timestamp(6) with time zone,
    id uuid NOT NULL,
    recipe_id uuid,
    reported_user_id uuid,
    reporter_id uuid NOT NULL,
    reason character varying(20) NOT NULL,
    comment text,
    recipe_name text,
    reported_username character varying(255),
    CONSTRAINT content_reports_reason_check CHECK (((reason)::text = ANY (ARRAY[('INAPPROPRIATE'::character varying)::text, ('SPAM'::character varying)::text, ('NOT_THEIRS'::character varying)::text, ('OTHER'::character varying)::text])))
);


--
-- Name: entity_translations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.entity_translations (
    locale character varying(10) NOT NULL,
    entity_id uuid NOT NULL,
    id uuid NOT NULL,
    entity_type character varying(255) NOT NULL,
    field_name character varying(255) NOT NULL,
    value text NOT NULL
);


--
-- Name: ingredient_unit_weights; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ingredient_unit_weights (
    grams real NOT NULL,
    id uuid NOT NULL,
    ingredient_id uuid NOT NULL,
    unit_id uuid NOT NULL
);


--
-- Name: ingredients; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ingredients (
    calories_per_100g real,
    carbs_per_100g real,
    fat_per_100g real,
    protein_per_100g real,
    id uuid NOT NULL,
    name character varying(255)
);


--
-- Name: password_reset_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_reset_tokens (
    expiry_date timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    token character varying(255) NOT NULL
);


--
-- Name: photo_uploads; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.photo_uploads (
    created_at timestamp(6) with time zone,
    uploader_id uuid NOT NULL,
    file_name character varying(100) NOT NULL
);


--
-- Name: recipe_categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recipe_categories (
    category_id uuid NOT NULL,
    recipe_id uuid NOT NULL
);


--
-- Name: recipe_ingredients; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recipe_ingredients (
    quantity real NOT NULL,
    id uuid NOT NULL,
    ingredient_id uuid,
    recipe_id uuid,
    unit_id uuid
);


--
-- Name: recipe_notes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recipe_notes (
    created_at timestamp(6) with time zone,
    id uuid NOT NULL,
    recipe_id uuid NOT NULL,
    user_id uuid NOT NULL,
    note text NOT NULL
);


--
-- Name: recipe_photos; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recipe_photos (
    recipe_id uuid NOT NULL,
    url text
);


--
-- Name: recipe_shared_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recipe_shared_users (
    recipe_id uuid NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: recipe_trash; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recipe_trash (
    deleted_at timestamp(6) with time zone,
    recipe_id uuid NOT NULL
);


--
-- Name: recipe_video_links; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recipe_video_links (
    recipe_id uuid NOT NULL,
    url text
);


--
-- Name: recipes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.recipes (
    is_public boolean NOT NULL,
    is_trashed boolean NOT NULL,
    publish_locked boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    copied_from_id uuid,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    visibility character varying(16) DEFAULT 'ONLY_ME'::character varying NOT NULL,
    description text,
    name text NOT NULL,
    removal_reason text,
    CONSTRAINT recipes_visibility_check CHECK (((visibility)::text = ANY (ARRAY[('ONLY_ME'::character varying)::text, ('FAMILY'::character varying)::text, ('PUBLIC'::character varying)::text])))
);


--
-- Name: units; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.units (
    grams_per_unit real,
    id uuid NOT NULL,
    abbreviation character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    CONSTRAINT units_type_check CHECK (((type)::text = ANY (ARRAY[('WEIGHT'::character varying)::text, ('VOLUME'::character varying)::text, ('COUNT'::character varying)::text, ('OTHER'::character varying)::text])))
);


--
-- Name: user_blocks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_blocks (
    created_at timestamp(6) with time zone,
    blocked_id uuid NOT NULL,
    blocker_id uuid NOT NULL,
    id uuid NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    suspended boolean DEFAULT false NOT NULL,
    created_at timestamp(6) with time zone,
    password_changed_at timestamp(6) with time zone,
    terms_accepted_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    id uuid NOT NULL,
    email character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    preferred_locale character varying(255) NOT NULL,
    role character varying(255) NOT NULL,
    username character varying(255) NOT NULL
);


--
-- Data for Name: blacklisted_tokens; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.blacklisted_tokens (expiry_date, id, token) FROM stdin;
\.


--
-- Data for Name: categories; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.categories (id, parent_category_id, name) FROM stdin;
fc9e4e89-44f6-4c2c-a359-1d35faabf2f8	\N	Cakes
3a34372a-bbd4-490b-8930-32122ea92d19	\N	Baking
ab0b31b0-f9a6-409e-93aa-e6726c356d30	\N	Desserts
84c1703a-8396-46f3-8a16-409c6eb54b49	\N	Pancakes
87376f92-ed2d-4c66-b2d2-dde377cd7e96	\N	Preserves
93e8f305-6ac4-45e2-a3de-24e17ed6caea	\N	Salads
4de3877a-6575-4077-b21a-b3019b31b0df	\N	Appetizers
ebfd861d-5bc0-4f0b-9586-9f2b95fd8d72	\N	Sauces
779fc600-3da9-4638-8cfc-67b1caa12eda	\N	Main dishes
743f2bca-cd79-4816-8d3f-0409e5213d64	\N	Soups
2dbed822-1614-4e2b-a49d-c803ccccc391	\N	Porridge
30242487-07db-4d5e-97f9-7003304ba5ee	\N	Creams
\.


--
-- Data for Name: content_reports; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.content_reports (created_at, id, recipe_id, reported_user_id, reporter_id, reason, comment, recipe_name, reported_username) FROM stdin;
\.


--
-- Data for Name: entity_translations; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.entity_translations (locale, entity_id, id, entity_type, field_name, value) FROM stdin;
\.


--
-- Data for Name: ingredient_unit_weights; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ingredient_unit_weights (grams, id, ingredient_id, unit_id) FROM stdin;
60	a1be5b81-325a-4887-b4e3-4c6647246e13	e289be71-fabc-4626-9c6b-b29053910c02	d70072fb-5bfb-4582-99a6-adf0c498a9e0
7	af3a097f-3dd1-4315-90d2-53d969328e1f	f73552ff-7a05-4987-ae8c-9ac37576582d	fb95a96c-41fb-457a-a922-523e7a3f8390
20	3a1fb0bf-4fdf-4ef3-9ea0-af27d0b11010	f73552ff-7a05-4987-ae8c-9ac37576582d	784b74d9-84cc-42d9-8d8b-1ea314520ecc
200	2915f617-5ac9-4319-969a-1ac940e514c5	f73552ff-7a05-4987-ae8c-9ac37576582d	c1ec0914-8074-469a-882b-e6d6b327864d
3	ef3b997b-4ceb-4a72-9f61-6ea4c9fd2c09	857c4409-ee1c-4f61-85fb-07baf592ac17	fb95a96c-41fb-457a-a922-523e7a3f8390
10	63c3490b-27ff-4dde-9703-3d71638300db	857c4409-ee1c-4f61-85fb-07baf592ac17	784b74d9-84cc-42d9-8d8b-1ea314520ecc
160	df298a57-a851-4d91-ab5f-07cba6ce9d74	857c4409-ee1c-4f61-85fb-07baf592ac17	c1ec0914-8074-469a-882b-e6d6b327864d
20	a04b8c3f-bc1f-453f-ac99-f8f14ea8e717	76760cc3-d871-4dda-91c5-c1a65a7b1fcf	784b74d9-84cc-42d9-8d8b-1ea314520ecc
20	7689a8e9-cff2-457c-b7a9-237f5f34a7d4	ec8b89c9-d251-4d81-970c-434c2f627942	784b74d9-84cc-42d9-8d8b-1ea314520ecc
9	edc16a63-1a40-49a6-9ae3-c8043a2ad3b6	41e7f61a-3a79-4188-b28a-51fd4ee5d762	784b74d9-84cc-42d9-8d8b-1ea314520ecc
12	609a0fa0-ca08-40d6-8fe9-fb5316351b67	12793ab8-c603-4b23-8e8d-9b9bb5675440	784b74d9-84cc-42d9-8d8b-1ea314520ecc
200	4ce83aba-dbf2-4320-9282-ccc3496f73da	27a76345-56b1-46c9-bfa1-2647400989d2	c1ec0914-8074-469a-882b-e6d6b327864d
16	6a2721ae-cc31-48c8-a7f5-2c0e8f2bee7f	ef4a022e-327f-4969-97c7-c7da025ce2ac	784b74d9-84cc-42d9-8d8b-1ea314520ecc
100	70da472e-3d61-4958-93da-d3e4a46b7b01	d620ea76-ef39-465b-bebe-384fef86a5e8	d70072fb-5bfb-4582-99a6-adf0c498a9e0
28	b3de6a13-a5f5-4d1f-bb1f-92e0fd9eef2b	b897ca34-aed8-47cf-82d2-b24be09146ec	784b74d9-84cc-42d9-8d8b-1ea314520ecc
35	cdd85fbc-3d48-469d-a634-70053a6a27d0	c63297da-47ab-4da6-857b-32f179082436	d70072fb-5bfb-4582-99a6-adf0c498a9e0
200	af99c201-0fff-4ad9-b143-daa187b51751	413e3168-c2dd-493e-a073-d8ea1946f8d7	d70072fb-5bfb-4582-99a6-adf0c498a9e0
6	eaf5696e-c732-4bdd-bb6f-d8f931981340	d7b0f95b-7480-4d64-a10c-98ca185baba3	fb95a96c-41fb-457a-a922-523e7a3f8390
150	1c857097-d3e3-4de3-ad3b-6fe6402c8a6e	41e215e0-3ed7-4b1b-8698-7a9bfaba45cc	d70072fb-5bfb-4582-99a6-adf0c498a9e0
7	f47afc48-709e-444c-9061-6f6efad95494	978d632c-a2b9-496f-9fb9-e613c217ff45	fb95a96c-41fb-457a-a922-523e7a3f8390
25	370e8cdf-bc98-4851-a434-898876550eaa	978d632c-a2b9-496f-9fb9-e613c217ff45	784b74d9-84cc-42d9-8d8b-1ea314520ecc
18	ad540e04-7d6e-48ea-945e-e6ebd40f5bf4	53da8bbf-ab8a-4bcc-b36a-8e5fad3cc818	d70072fb-5bfb-4582-99a6-adf0c498a9e0
200	ff7870b5-3ccd-4068-8127-ae216e218cd9	cfa077e3-1b23-44c3-abce-f1a06d7253a1	c1ec0914-8074-469a-882b-e6d6b327864d
15	1348c06c-8510-4936-b8dc-3340dca6b6e8	139f0e32-04a6-4583-9d57-8b21f2ea1e67	d70072fb-5bfb-4582-99a6-adf0c498a9e0
1500	8efe54fe-00b5-4e9c-9e86-1e020638fab0	976b7d01-4b0e-4e2a-b544-4e6a530e9ab9	d70072fb-5bfb-4582-99a6-adf0c498a9e0
100	2f124c95-673d-4b04-8dae-c3fabc143791	dae09374-8ccb-41a2-890a-987fe6b9a85a	d70072fb-5bfb-4582-99a6-adf0c498a9e0
18	b399ca0e-7e72-4a69-862f-3fdb58482c5f	4dd4f4db-2f74-496b-a58b-713052e287b7	784b74d9-84cc-42d9-8d8b-1ea314520ecc
4	81c16114-db77-46d6-9bbb-c98af7cae2ac	5ef86564-586b-4e7d-a436-fe4b39d07dee	fb95a96c-41fb-457a-a922-523e7a3f8390
190	2cf3d4b1-6ac0-45b1-9dcb-b88b81c3e628	8d9fa50d-970a-41f1-a8d6-449a376dcf87	c1ec0914-8074-469a-882b-e6d6b327864d
17	e9b29fd5-2e9c-496e-b20a-3039fe95fff5	8d9fa50d-970a-41f1-a8d6-449a376dcf87	784b74d9-84cc-42d9-8d8b-1ea314520ecc
12	59d1f398-92c6-4cd5-9568-d16a258924c7	0ef7064e-9a76-48d6-bfeb-9936fb2fe427	784b74d9-84cc-42d9-8d8b-1ea314520ecc
100	025cbc83-84ef-4d2c-bae0-038f34dc232d	4f7a14bd-f91c-4a18-8619-f516c21764a2	d70072fb-5bfb-4582-99a6-adf0c498a9e0
120	9f416ef0-4af5-4aa9-90b9-f57ede2dffe5	67f9d789-1df0-4f5d-90e2-bf27216edcdf	d70072fb-5bfb-4582-99a6-adf0c498a9e0
150	90a8c418-9648-4e84-8f2d-0232764bc53e	94015021-6a51-48d2-abce-a45bb9dc1758	d70072fb-5bfb-4582-99a6-adf0c498a9e0
5	b82ca8d2-c2dc-43c7-8f03-076af53b553a	ac42ef71-3c3e-4100-9874-e25b424efd09	d70072fb-5bfb-4582-99a6-adf0c498a9e0
10	7738a338-4c09-46dc-945b-f768045a4c5b	fb679be6-e40e-4eeb-99ab-e97183f63bbc	d70072fb-5bfb-4582-99a6-adf0c498a9e0
20	2b16d092-b3ee-40cd-a8fb-2acc7e3934d7	d8f01542-b604-4b01-85d4-d49f4f483e6f	784b74d9-84cc-42d9-8d8b-1ea314520ecc
85	6a60aabb-c516-462c-8d34-b91babfd8495	c5305ddc-d522-484c-9433-2a289fc89d4e	d70072fb-5bfb-4582-99a6-adf0c498a9e0
15	ba1737ce-e981-4f5b-b43b-b595a06113e6	f48184bb-a78f-4806-9aa7-f33f4b24a0da	784b74d9-84cc-42d9-8d8b-1ea314520ecc
0.5	1acc474e-5176-4866-b345-741b77eec1f1	3cbdd511-7f1e-4297-ab9b-2ed31a7ae206	d70072fb-5bfb-4582-99a6-adf0c498a9e0
10	cb21a1f6-aa22-404b-938e-1955b9f5eb1c	d90aa4f2-7551-48d3-a4ca-52d318c4c79a	d70072fb-5bfb-4582-99a6-adf0c498a9e0
0.2	ca5dc989-af13-4739-b729-8efae85ec79c	9b9a609f-bb00-49cc-b62b-94c58fc2ba7f	d70072fb-5bfb-4582-99a6-adf0c498a9e0
60	1a07e58b-fabd-43a0-9358-17cd7326a847	f9aaecb1-0b8a-4493-bedd-053296a4d6d1	d70072fb-5bfb-4582-99a6-adf0c498a9e0
100	d0230525-f6c8-4f68-86d3-9ff1eff64285	52e6617a-e2e4-47b7-8e48-6fba43d7076f	d70072fb-5bfb-4582-99a6-adf0c498a9e0
16	c557986d-0805-48c8-a896-63d098352497	ee900e71-fea5-4d34-bd85-85deae900a34	784b74d9-84cc-42d9-8d8b-1ea314520ecc
180	cd0c1d11-910b-4ed4-86b0-9ddffcdfadc2	e240c939-bfae-4cb1-9677-6862aa99a796	c1ec0914-8074-469a-882b-e6d6b327864d
170	836f10d2-1661-438f-a6c9-4a1262dfa6ec	e0220a0a-aa8c-442e-ba47-83a5a1a02b3a	c1ec0914-8074-469a-882b-e6d6b327864d
200	9a538570-41fc-444f-bd43-986762e555d4	c3547431-367c-4141-b704-383d28a988ee	c1ec0914-8074-469a-882b-e6d6b327864d
130	e5748b6d-d385-4347-9abe-adcd28209479	13cff322-1d79-4891-be4f-ea6d7fe6b445	c1ec0914-8074-469a-882b-e6d6b327864d
20	81916874-ee45-4be1-b079-af9158484ca4	edf13393-d18e-4c77-9adc-303acd925b11	784b74d9-84cc-42d9-8d8b-1ea314520ecc
17	fa0e2660-d506-49a9-96f0-da2e85f4e238	a8dbb379-0155-468c-86c8-3f03d6fd2505	784b74d9-84cc-42d9-8d8b-1ea314520ecc
14	899fd6f4-441e-4232-96c5-a779800bfee5	1fc2937e-8c7d-405d-a453-3077c5be3e60	784b74d9-84cc-42d9-8d8b-1ea314520ecc
12	ea31f2d4-246d-4907-b03b-224cd7b0c0e3	c3547431-367c-4141-b704-383d28a988ee	784b74d9-84cc-42d9-8d8b-1ea314520ecc
10	7ed154ca-12ad-4255-846c-8825b721bd6c	13cff322-1d79-4891-be4f-ea6d7fe6b445	784b74d9-84cc-42d9-8d8b-1ea314520ecc
4	c779d583-f98a-4f21-8f9c-37d05d4c7311	77d4f463-f0e3-475c-9904-09204b3db6eb	fb95a96c-41fb-457a-a922-523e7a3f8390
6	6c79c67e-44e2-4014-9703-0fcf92370e57	63b0f6c6-17cd-41ca-bdcd-058cd79fb946	fb95a96c-41fb-457a-a922-523e7a3f8390
180	c69cf708-51dc-46a8-a36d-fc1dc38b0209	c08ca014-98ce-4e9f-a4b9-e1b14a7000de	d70072fb-5bfb-4582-99a6-adf0c498a9e0
60	fd24ca66-bd34-448c-a690-4910265598ac	8e0c6f2d-3ca8-4d9a-a48c-f814bde2ef5f	d70072fb-5bfb-4582-99a6-adf0c498a9e0
55	912de25c-85ba-4b54-84a6-b471319f3c14	28d8aed7-72f9-4321-8abc-738260fc31ae	d70072fb-5bfb-4582-99a6-adf0c498a9e0
80	ff7ae27a-fcb5-479e-92a0-25c7f61c2596	402a9200-5930-4f5e-b11e-75ab1afca62a	d70072fb-5bfb-4582-99a6-adf0c498a9e0
150	80274be2-8227-4924-bce7-03c19f4a6a12	752bb249-2883-4939-9bfd-6854a1d65860	d70072fb-5bfb-4582-99a6-adf0c498a9e0
0.2	726ac6da-a597-4aff-a979-eebd6f2a75bf	4532ebaf-aebf-43d9-8e57-f3e0240f1b16	d70072fb-5bfb-4582-99a6-adf0c498a9e0
5	766dd083-5c89-4a13-a25f-52ab84a2eed4	677b90b8-dca6-48ac-bff0-64aa54bf1f33	d70072fb-5bfb-4582-99a6-adf0c498a9e0
100	8a072407-8bcc-4b58-a470-229c59c2896b	272ed040-4240-43c3-8bb4-082ae8017e40	d70072fb-5bfb-4582-99a6-adf0c498a9e0
80	91ac467d-8f29-46cb-8798-c5845485637c	09e1da31-542c-4efc-92b1-588ef6005f3e	d70072fb-5bfb-4582-99a6-adf0c498a9e0
150	5ecea34b-7261-4185-9950-124f40d2fdc4	239b5014-092c-4dc0-b670-46a024506d63	d70072fb-5bfb-4582-99a6-adf0c498a9e0
250	033718f0-c3e4-48a6-8c39-b88c75db7c52	69ba1681-30a5-41c4-b3ae-caaa7222ce24	d70072fb-5bfb-4582-99a6-adf0c498a9e0
55	e8945b80-cc2e-43ff-8f0a-0693d7f85bce	94463598-2eb0-4af3-90cd-a35401114cc1	d70072fb-5bfb-4582-99a6-adf0c498a9e0
250	a5ef3bbe-8996-469b-bdaf-c6dde34336d8	f5e0c44a-c60f-4c67-b823-6fd115249368	482802f4-4681-425a-84d5-561bf8e07b46
\.


--
-- Data for Name: ingredients; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ingredients (calories_per_100g, carbs_per_100g, fat_per_100g, protein_per_100g, id, name) FROM stdin;
157	0.7	11.5	12.7	e289be71-fabc-4626-9c6b-b29053910c02	яйцо
398	99.7	0	0	f73552ff-7a05-4987-ae8c-9ac37576582d	сахар
342	74.2	1.2	9.2	857c4409-ee1c-4f61-85fb-07baf592ac17	мука
0	0	0	0	3c3f0ad0-5ce7-4c1a-b7e8-f85e1478780b	сода гашеная
717	0.1	81.1	0.8	76760cc3-d871-4dda-91c5-c1a65a7b1fcf	масло сливочное
204	3.4	20	2.5	ec8b89c9-d251-4d81-970c-434c2f627942	сметана
289	10.2	15	24.3	41e7f61a-3a79-4188-b28a-51fd4ee5d762	какао
45	9	0.4	1	cc7262f5-f7dc-476f-bf18-f6df045d6d34	ягоды
320	56	8.5	7.2	0ec43594-2329-4b73-97ee-f9d22501c553	сгущенка
399	99.8	0	0	12793ab8-c603-4b23-8e8d-9b9bb5675440	сахарная пудра
64	4.8	3.6	3.2	27a76345-56b1-46c9-bfa1-2647400989d2	молоко
333	70.6	1	10.3	ef4a022e-327f-4969-97c7-c7da025ce2ac	манка
29	9.3	0.3	1.1	d620ea76-ef39-465b-bebe-384fef86a5e8	лимон
304	82.4	0	0.3	b897ca34-aed8-47cf-82d2-b24be09146ec	мед
44	0.7	0	11.1	c63297da-47ab-4da6-857b-32f179082436	белок
50	4	2.5	3	9d58b298-1f44-484a-8edd-a191cc551958	кефир
47	11.8	0.1	0.9	413e3168-c2dd-493e-a073-d8ea1946f8d7	апельсин
0	0	0	0	d7b0f95b-7480-4d64-a10c-98ca185baba3	сода
539	48.2	35.4	6.2	fc1a880a-ac15-4400-a9e8-2dfbdf349729	шоколад
315	55	8.5	6	1f62718a-84c9-4937-b542-a6a280c8592b	сгущенка вареная
64	4.8	3.6	3.2	a09a29f3-63e8-4389-8bce-e178fa025326	молоко кипяченое
300	52	8	7	b0a9d2eb-d009-4823-b923-f46ca25aa47e	сгущенка кофейная
250	62	0	0.3	54fd7d69-14a1-4571-8941-b170063d4f07	варенье кисло-сладкое
52	13.8	0.2	0.3	41e215e0-3ed7-4b1b-8698-7a9bfaba45cc	яблоки
0	0	0	0	978d632c-a2b9-496f-9fb9-e613c217ff45	соль
300	60	4	4	6c86ed9c-fd67-483c-8fa5-92264406eb4a	сгущенка (или варенье)
159	3	9	16.7	1bed1615-9577-443c-8778-978be2936712	творог
352	0.4	31.2	16.2	53da8bbf-ab8a-4bcc-b36a-8e5fad3cc818	желток
260	65	0	0.4	39286779-8759-4e66-b0c1-f4da567828a6	джем или повидло
250	50	2	1	6471cf4f-c79e-4e5a-a22f-0c0562517f73	начинка
260	65	0	0.3	c175bf60-7620-47b6-b150-cf0837d7fc73	варенье без косточек
0	0	0	0	cfa077e3-1b23-44c3-abce-f1a06d7253a1	вода
0	0	0	0	dc53fbcb-07a7-4636-babe-a7e4ebb4b201	лимонная кислота
260	65	0	0.4	a6b14d63-f1c5-48a5-8ca1-e3ed365f5000	повидло
450	65	18	7.5	139f0e32-04a6-4583-9d57-8b21f2ea1e67	печенье
650	10	60	15	9831f501-ed30-4a02-969a-4cc0ecdb3df1	орехи
75	0	2.7	12.7	b26107a6-c131-470e-ad41-6344bcc05994	дрожжи
0	0	0	0	49241cdc-66ac-4dc0-9dde-7b7bbf201fd0	вода холодная
120	4	10	3	9aa7ab07-20a9-4a00-aeaf-0ad6b8bee3fd	сливки и молоко
97	14.4	0.2	1.5	916d0703-a200-4f84-98b6-946b4be71bdc	цедра апельсина
25	5.8	0.1	1.3	976b7d01-4b0e-4e2a-b544-4e6a530e9ab9	капуста
250	0	20	18	81b9700a-6570-4640-a80b-0bd41d3381e7	мясо
20	4.5	0.2	1	4cf382fe-20dc-44be-bb8b-9a9ee6ae7193	перец
22	3.3	0.3	3.1	8033d18a-595a-4e70-b575-252d02b6f721	грибы
892	0	99	0.2	c79ec272-9b80-4349-bebe-7e451cad36d3	масло топленое
40	9.3	0.1	1.1	dae09374-8ccb-41a2-890a-987fe6b9a85a	лук репчатый
335	0.7	0.4	87.2	8bb59165-380f-4470-94f1-4209732587ed	желатин
240	57.5	0.4	2.2	287da0f9-5526-4980-928e-67146bbe2a6e	чернослив
288	12.5	0.1	1.2	ddbe3307-12ae-45fb-8ca3-9dcba8ddc13f	ваниль
200	3.7	20	2.8	91117ab2-bfec-450e-bd85-fc0aad1eedc5	сливки
288	12.7	0	0	d11f7846-abdd-4405-b4db-5bb4a8cbb36f	ванилин
337	35.8	18.4	6.1	ef439dc5-c57d-418c-98bd-ab4fea9a01ba	слоеное тесто
150	0	8	18	a1171b22-d092-4120-8567-552cbafedac3	рыба
350	0	26	25	e60eb3ad-646a-4b02-afa7-811653d6a934	сыр
680	3.9	67	2.4	4dd4f4db-2f74-496b-a58b-713052e287b7	майонез
30	4	0.5	2.5	bec17728-a88f-415b-9409-f5ff759b15f6	зелень
299	79.2	0.5	3.1	19226005-ab5c-4892-852e-b6bbe6a730d7	изюм
320	80	0	1	11e2e350-b66e-4910-ad92-f033568e87a9	цукаты
579	21.6	49.9	21.2	bc355240-b7c2-46f8-9dac-19671b1dbb91	миндаль
22	6.9	0.2	0.4	3bacf36a-7001-4713-86b4-559deba6cb52	сок лимона
79	19.6	0	0.1	5ef86564-586b-4e7d-a436-fe4b39d07dee	разрыхлитель
247	50.6	1.2	4	8326e5fa-a53d-4961-a681-ccf7f5d6d631	корица
398	99.5	0	0	405faaf4-bccb-478b-8ebe-69706e09a48f	сахар ванильный
412	4.8	41.5	4.8	3426e951-a117-420b-9480-35201d7fed67	сыр маскарпоне
239	0.1	0	0	d758dc30-cafd-4d99-91e7-afeff47783a5	коньяк
2	0.3	0	0.2	edea4f82-8575-41f8-a12b-cd623a15090a	кофе
325	26	6	40	2cb1c8e8-79e6-410a-b6c3-2ea79793b53f	дрожжи сухие
44	7.6	0.4	1.1	841d606a-2278-4f20-acf2-3e501ef743a6	черника
884	0	99.9	0	8d9fa50d-970a-41f1-a8d6-449a376dcf87	масло растительное
348	83.5	0	0.1	0ef7064e-9a76-48d6-bfeb-9936fb2fe427	крахмал
32	7.7	0.3	0.7	3f2d0798-ab64-4bb6-bf8f-90967010b76b	клубника
150	35	0.1	0.5	f8d50c1f-dd5c-421f-8676-7a1837262760	соус клубничный
70	14.9	0.9	3.8	ec81be6f-b943-480c-8edd-c04ae223185e	мята свежая
40	4	1	2	71e41400-4960-4770-92cf-79585a5222e6	молоко или сыворотка
77	17.5	0.4	2	4f7a14bd-f91c-4a18-8619-f516c21764a2	картофель
660	23.7	64.5	6.8	3b4abab8-2889-45ad-b388-db8fed9e7478	кокосовая стружка
18	3.9	0.2	0.9	67f9d789-1df0-4f5d-90e2-bf27216edcdf	помидоры
26	6	0.3	1	94015021-6a51-48d2-abce-a45bb9dc1758	перец болгарский
149	33.1	0.5	6.4	ac42ef71-3c3e-4100-9874-e25b424efd09	чеснок
40	7.3	0.4	1.9	fb679be6-e40e-4eeb-99ab-e97183f63bbc	перец горький
48	11.3	0.1	1.2	dd89831f-6f4d-4228-b4fd-23aa849d67ad	корень хрена
82	18.9	0.5	4.8	d8f01542-b604-4b01-85d4-d49f4f483e6f	томатная паста
18	3.9	0.2	0.9	ecb43889-ade2-4171-beb7-3ab26a1fe48c	помидоры свежие
41	9.6	0.2	0.9	c5305ddc-d522-484c-9433-2a289fc89d4e	морковь
884	0	99.9	0	be4971d0-e324-4bbd-9fcb-880a363a7516	масло подсолнечное
18	0.1	0	0	f48184bb-a78f-4806-9aa7-f33f4b24a0da	уксус
1	0.1	0	0.1	3cbdd511-7f1e-4297-ab9b-2ed31a7ae206	лист смородины
1	0.1	0	0.1	c5c2d687-96ae-4b53-beb6-31f85db7e4ee	лист вишни
43	7	1.1	3.5	d90aa4f2-7551-48d3-a4ca-52d318c4c79a	укроп
313	48.7	8.4	7.6	9b9a609f-bb00-49cc-b62b-94c58fc2ba7f	лавровый лист
251	38.7	3.3	10.4	2629bb3b-390e-461e-8bba-3f6334397352	перец черный
15	3.6	0.1	0.8	734a6398-628c-4a79-9936-f821fdc4711e	огурцы
1	0.1	0	0.1	cbb49367-0a57-4b3d-bf77-b5de1b657baa	лист хрена
17	3.1	0.3	0.6	4f568d0e-4863-4fb0-94d4-01fa247f30c0	кабачки
0	0	0	0	1b37d506-8056-4a9a-b655-0e0fbc6a276c	уксусная эссенция 70%
11	0.1	0	0	cb8edadc-4fce-4f33-80ef-e13ba40679fe	уксус 6%
21	4	0.1	1	1935ef92-dca7-4598-891a-0f376629ca97	сок томатный
25	5.9	0.2	1	d7e1a7d3-dc97-4df0-8ae3-72e74727f6f0	баклажаны
149	33.1	0.5	6.4	bdcd5970-8326-4f5e-85c7-27dd67e566de	чеснок очищенный
11	0.1	0	0	53f16cad-a89c-4bfd-8dbd-bc62e7cc5b7a	уксус 3%
40	7.3	0.4	1.9	b300a8af-3265-4041-9f50-abfee65aa807	перец красный
251	38.7	3.3	10.4	7bcc15db-d1da-4add-8274-1de59f00dcb8	перец горошком
44	8.2	0.4	1	347ba071-4a41-4298-9678-1ac4b4fc8c4a	смородина
11	0.1	0	0	1b2f64f6-791f-437c-94a1-d29d985c8eb8	уксус 9%
0	0	0	0	2dbda833-6368-4229-bd57-334465706b5a	вода кипяченая холодная
18	3.9	0.2	0.9	d639dc43-e6ad-4d06-9b3c-a8efdd33a4ae	помидоры мятые
26	6.5	0.1	1	c9865f23-ebb3-4bb1-9547-dc0db70f5c4c	тыква (нетто)
52	13.8	0.2	0.3	7a5262b0-2624-4df5-b933-e75774edefc3	яблоко очищенное
43	9.6	0.2	1.6	e6fa672d-ed7d-4323-90aa-90fbad85e870	свекла
200	30	5	5	8bbf8f16-fb76-4447-ba6f-2e4be56dd78a	специи
0	0	0	0	9425655f-b0ac-462d-b686-5c5b18624249	эссенция 70%
263	38.7	8.7	6	cb21c20a-1165-4dca-b8ef-e115fe50abfa	перец душистый
0	0	0	0	5af599c8-7ea8-489f-aed6-c02e81f1927a	вода кипяченая
290	47	1.6	21	a613251b-3b5a-4a0d-8f02-28781c608ca0	фасоль
52	13.8	0.2	0.3	c3de51cd-b23e-457f-b0bb-a36f538f5eee	яблоки очищенные
162	8.2	12	5.7	f25bd021-c82c-4bb0-872f-3e46cc230f0b	горчица
884	0	99.9	0	98f29083-27bc-44d2-9218-e2ba09ccdc94	масло оливковое
654	13.7	65.2	15.2	37c53a34-7779-4f05-8df7-e7c0a605a7c1	грецкий орех
45	9	0.4	1	66b4fb38-4525-4117-a52e-ce6645899b02	ягоды замороженные
70	8	2.5	3.5	a4d775a6-d6f0-41b1-a10a-cac297ab0bf8	йогурт питьевой
251	38.7	3.3	10.4	b2c6a2e0-dd88-4fc1-92a6-6f36da39ada8	перец молотый
350	71	1.5	12	6692e0ce-e293-4cff-939f-4ef3fa540483	спагетти
200	0	13	20	7a03b9f2-7b79-4aaf-a99f-7aa14ea9cc96	лосось
155	1.1	10.6	12.6	f9aaecb1-0b8a-4493-bedd-053296a4d6d1	яйцо вареное
190	0	13	18	1db6d630-25e7-4345-a388-c2cedf765080	курица
23	0.9	0.5	2.1	edfcda2c-17ff-449a-9a0a-78eeba77f498	кориандр
220	0	13	25	e50397a5-1d62-4557-99cd-947a262dd64f	мясо вареное
35	8	0.2	0.8	6519ef19-589b-4bd5-a149-a896acad5aff	морковь вареная
47	9.8	0.4	0.4	4bcba1ae-975b-4c80-a63e-86bfe3f0e98b	яблоко кислое
49	10	0.1	1.8	daf438eb-fe54-491e-8413-461a818695af	свекла вареная
72	14.5	0.6	0.7	44314887-7417-4c2e-aad5-ce1fd0ec19d2	гранат сладкий
270	2	19	21	0b20ed77-826f-402b-b154-0be25d07a9d1	сыр колбасный
81	14.5	0.4	5.4	d45f4b14-a181-4534-8c70-8be956cff1d9	зеленый горошек
380	58	12	10	6d8b2ad3-fe96-4666-802e-6c815833d5c3	гренки
16	2.3	0.1	0.7	d3968e3b-52a3-4219-9cac-64cb5d7c3cbf	огурцы маринованные
250	1	20	15	d586a110-a4ee-47dd-aff7-c70a10f5d72a	сыр мягкий
120	0	5	18	e0a6cf73-bde8-43d4-946b-71165ae1cb62	рыба вареная
82	16.7	0.4	2	4a298526-7350-46a2-a193-2d791d6478a0	картофель вареный
15	3.6	0.1	0.8	52e6617a-e2e4-47b7-8e48-6fba43d7076f	огурец
170	0	7	25	5ee4cfb2-2fdf-4326-8d5b-878ceb893886	курица вареная
270	0.5	24	14	e335c3bf-4cd2-496e-a7d1-915f000bfc4f	ветчина
25	5.8	0.1	1.3	208f64e5-d37e-40a3-ad7d-039391ee423e	капуста свежая
312	41	15	3.4	78e49834-7c1e-437b-b4f8-3f0c76be4fbc	картофель фри
86	19	1.2	3.2	d84914b9-53c3-4fc1-a8aa-c5daa4780403	кукуруза
94	0.6	2.1	18	7695f746-5a64-4e62-96a9-ef9836b0f3b1	желудки куриные
159	0.8	10	16	83cddb2f-16e7-4dbd-bddd-83c7469e3163	сердце куриное
50	13.1	0.1	0.5	1cd51b86-a5f5-4ffc-9e41-fea7054347b5	ананас
95	15	1	6	c9adaa12-cb0b-403e-86bf-6c00c02077b8	крабовые палочки
0	0	0	0	9eefc0bd-8825-4c65-a004-0f228d85b65a	уксусная эссенция
417	10	40	5	6a5494fd-2a67-4017-aea0-e63c6c9bd0af	хмели-сунели
36	7.6	0.4	3	83b940be-f0a0-40c2-b214-c2244a737b34	петрушка
53	7	0.1	6	ee900e71-fea5-4d34-bd85-85deae900a34	соевый соус
150	0	7	20	ec4d93a5-f555-4431-9321-0d4e14ef7bdb	филе куриное или рыбное
130	5	4	18	e4622abc-b2f6-42d9-a1d8-8ca2ebdc8bd6	печень
350	71	1.5	12	6b076812-261f-4177-9eb3-bdc1d1ef4e59	макароны
27	0.1	1	4.3	7168e7b0-d692-410f-8b41-9ce4548aa7b5	шампиньоны
250	5	20	10	dddf6482-b4ea-4530-b6a4-a6596b74a020	сыр плавленый
137	0	1.8	29.8	f916735a-df55-42e3-9737-042b4ee5adab	куриная грудка вареная
15	3.6	0.1	0.8	abd10058-efed-4ca2-847d-4098d8d7da2d	огурец свежий
113	9	8.2	1.2	3323aed5-0977-42f3-91d8-574bd9ae9207	морковь по-корейски
20	3.2	0.1	1.3	ea31181e-7c91-4a6f-8955-f97d5d0ff3e4	лук зеленый
20	3.9	0.1	2.2	1ba5263c-80ec-4f4c-a17e-6d9a0adbd658	спаржа
88	2.4	0.8	17.5	5b7501dc-a7ff-4ade-8e2a-b5806c22d201	гребешок
250	0	16	25	552fac77-ffc1-471d-99ff-d12b08c5e830	говядина отварная
654	13.7	65.2	15.2	ddbf6fb3-5d86-477e-b1a7-c924bd0e8c8b	грецкие орехи очищенные
282	54	12.9	14.1	23561844-c9e6-4e44-ab34-5a5d16a9e391	паприка
350	0	26	25	d330e95f-1ee8-4a7a-b2d0-d6f0de40059a	сыр твердый
83	18.7	1.2	1.7	556cffa8-9753-4848-ac7a-d528f3d2591c	гранат
22	0.5	1.2	2.2	7220047f-dd21-420b-864b-c07479da3703	опята
90	15	0.5	7	e31d7b6f-5c53-42cf-82f6-6a31b9781e19	фасоль красная консервированная
158	0.6	11	13	f7ba9a25-0f92-423f-a1e4-d2da851641b5	яйца перепелиные вареные
15	3.6	0.1	0.8	0f165011-78c0-4073-a866-cd91fc7cc633	огурцы свежие
130	28	0.3	2.7	8abeb0c1-cb45-4736-8b96-4be58d020450	рис вареный
99	0.2	0.3	24	0f990df8-86bc-4c61-9c37-34659d3bc921	креветки
170	0	10	20	7af449de-9395-4a05-acb8-a79021b6ef9d	печень вареная
183	0	11	21	0e029d5d-d687-4573-a964-0c72894b70c0	кролик (тушка)
265	69	4	9	703d0119-529d-4401-9b5a-db407159956a	орегано
300	2	22	22	d5dc091a-546b-4428-bb89-a9c2a5141362	моцарелла
18	3.9	0.2	0.9	d0f57c11-6ca5-4d35-8814-3276a4f16a7c	помидоры черри
23	2.7	0.6	3.2	03a1f903-63d8-437b-84ed-fdd1d199a7e3	базилик
10	0	0	0	7faab3c2-a81c-464b-ac5d-0a355d24649f	уксус винный
10	0	0	0	059206d7-d5de-42e6-afa4-3029f811734c	уксус крепкий
24	1.5	1	2.3	0671e46c-d7f5-4103-9386-0d5a7c84c1b5	грибы маринованные
113	0	1.9	23.6	abd544a1-6626-4964-be18-78cb67c568ca	куриная грудка
90	15	0.5	7	be8dd578-9c09-45f6-ac74-6edbaf5392fb	фасоль консервированная
15	0.5	0.5	2	23e33b9d-489a-45e3-a254-b313ad3735c9	бульон
340	77	0.6	7	e240c939-bfae-4cb1-9677-6862aa99a796	рис
160	0	9	18	d8c986d7-2b35-411c-8a73-5a7369504e31	селедка
140	0	6	21	341114c4-76a8-4455-858e-31991b27546a	горбуша в собственном соку
180	0	12	18	eb6ca113-2d27-46c0-90f7-a12f02fdbdb5	куриная голень
310	62	3.3	12.6	e0220a0a-aa8c-442e-ba47-83a5a1a02b3a	гречка
331	73	0.7	16	97a82a97-bd9f-4862-b1b9-e65f11d81cf4	чеснок сушеный
325	58	14	12	2328b2b3-d593-4254-9678-b6d675a506db	карри
149	33.1	0.5	6.4	552d5b1d-fdc5-4418-8411-2e186150f5c4	чеснок свежий
274	65	13	6	f0b13f5a-7019-4ac8-afe0-168675d2f286	гвоздика
45	10	0.2	0.7	3af5e8b0-4169-4856-8cf5-d4417df6ba1a	сок апельсина
174	3	13	11	40651748-0fd4-485a-8fba-7653bb88ae76	рикотта
231	0	0	0	7e73fe7d-00bc-4612-bb63-cd7dc9caed1f	ром
251	38.7	3.3	10.4	380bd3ce-6403-4abe-a2cc-3527f7393db4	перец черный горошек
508	28	36	26	b5cced77-e51e-4eeb-9ee1-c20e9296dc4a	зерна горчицы
235	0.1	0	0	2a35e142-fbcc-4f09-be01-f1c1585f17e8	водка
0	0	0	0	aa515739-fd83-4522-a205-f474eee03123	соль крупная
285	0	23	20	0cea981b-77ae-4574-b250-158f203289ec	сулугуни
240	0	18	18	1917d7dd-7b2e-4b8d-a414-d95ac3dde7e0	сыр адыгейский
23	3.6	0.4	2.9	42a3e4bd-d057-49bf-b666-8eb6a8039713	шпинат
43	7.7	0.2	0.6	10091fda-7078-499e-94aa-8cbd7ed0df18	красная смородина
348	69	3.3	11.5	8e6b60f5-a557-4c70-8c45-bf3f9ab3f9dc	пшено
352	61.8	6.2	12.3	6f77a280-0498-4105-ade9-648a968f4af9	геркулес
160	8.5	14.7	2	7d609e5b-4757-4931-b96f-335120c1637a	авокадо
72	16.8	0.1	2.5	103a8497-ca96-4646-920e-2ac3f3d601bf	лук шалот
23	3.7	0.5	2.1	ff77f397-d774-423f-97a4-17e2458d0a54	кинза
30	10.5	0.2	0.7	2b4e38b9-c7f0-40f7-b029-2cd5925e5fb8	сок лайма
156	2	9	18	5d178d9a-1999-4d14-be15-793b0829f165	cottage cheese (tvorog)
155	1	11	13	94463598-2eb0-4af3-90cd-a35401114cc1	egg
364	76	1	10	13cff322-1d79-4891-be4f-ea6d7fe6b445	flour
398	100	0	0	c3547431-367c-4141-b704-383d28a988ee	sugar
0	0	0	0	63b0f6c6-17cd-41ca-bdcd-058cd79fb946	salt
899	0	100	0	1fc2937e-8c7d-405d-a453-3077c5be3e60	sunflower oil
187	0	12	19	13c4850f-0940-4d4e-adda-8da7298677aa	beef
43	10	0	2	69ba1681-30a5-41c4-b3ae-caaa7222ce24	beetroot
27	5	0	2	bc4a6da3-01d6-40ea-af68-6cc2e53a6fdb	white cabbage
77	17	0	2	239b5014-092c-4dc0-b670-46a024506d63	potato
35	8	0	1	09e1da31-542c-4efc-92b1-588ef6005f3e	carrot
41	9	0	1	272ed040-4240-43c3-8bb4-082ae8017e40	onion
82	17	0	4	a8dbb379-0155-468c-86c8-3f03d6fd2505	tomato paste
143	30	1	6	677b90b8-dca6-48ac-bff0-64aa54bf1f33	garlic
313	48	8	8	4532ebaf-aebf-43d9-8e57-f3e0240f1b16	bay leaf
206	3	20	3	edf13393-d18e-4c77-9adc-303acd925b11	sour cream
52	5	3	3	acaaa9d1-2345-420d-96da-424cd467f397	milk
748	1	83	1	86f619a9-19c8-441e-b062-b880c33a2891	butter
82	17	0	2	752bb249-2883-4939-9bfd-6854a1d65860	boiled potato
35	8	0	1	402a9200-5930-4f5e-b11e-75ab1afca62a	boiled carrot
155	1	11	13	28d8aed7-72f9-4321-8abc-738260fc31ae	boiled egg
170	0	7	25	df9f57e1-3c43-42b1-a4f1-7911f17deac0	boiled chicken
13	2	0	1	8e0c6f2d-3ca8-4d9a-a48c-f814bde2ef5f	pickled cucumbers
55	10	0	4	f5e0c44a-c60f-4c67-b823-6fd115249368	green peas
32	7	0	2	0840f5b9-7b38-4d47-bd59-36863bd42192	green onion
627	3	67	1	bebc4d52-e3ea-4d07-96b3-c9bbb6283db0	mayonnaise
0	0	0	0	83d75424-943f-48dd-b734-402db1b76be7	cold water
254	0	20	17	69253823-149d-49b0-a073-180b4bf9064f	ground meat
255	64	3	11	329568ad-e2a5-4566-aacc-748fbe77e4a7	black pepper
130	28	0	3	5429a21c-55e2-460d-a256-e1875218c74a	cooked rice
52	14	0	0	c08ca014-98ce-4e9f-a4b9-e1b14a7000de	apples
53	28	0	0	77d4f463-f0e3-475c-9904-09204b3db6eb	baking powder
247	81	1	4	001e8e51-a6c2-4ec8-a4e2-d78b72a31179	cinnamon
\.


--
-- Data for Name: password_reset_tokens; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.password_reset_tokens (expiry_date, id, user_id, token) FROM stdin;
\.


--
-- Data for Name: photo_uploads; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.photo_uploads (created_at, uploader_id, file_name) FROM stdin;
2026-11-17 21:18:44.083373-08	6a2dd0c2-bfe3-42c6-ac41-817330a7a665	288917aa-b544-4f91-98c3-5afbaa5d9dc6.jpg
2026-11-17 21:18:44.391286-08	6a2dd0c2-bfe3-42c6-ac41-817330a7a665	397061e4-401e-4d64-b558-79318b52b9d6.jpg
2026-11-17 21:18:44.596252-08	6a2dd0c2-bfe3-42c6-ac41-817330a7a665	960cc1dc-b388-4e3a-9d1d-9a747165b6d4.jpg
2026-11-17 21:18:44.725973-08	6a2dd0c2-bfe3-42c6-ac41-817330a7a665	c263f2a0-f392-4986-9595-24ab3aba6046.jpg
2026-11-17 21:18:45.080931-08	6a2dd0c2-bfe3-42c6-ac41-817330a7a665	385602ab-0a19-4528-8173-333195aefa3b.jpg
2026-11-17 21:18:45.2905-08	6a2dd0c2-bfe3-42c6-ac41-817330a7a665	7b05df19-1dfa-4668-9a61-6bafed6c62cb.jpg
2026-11-17 21:18:45.497115-08	6a2dd0c2-bfe3-42c6-ac41-817330a7a665	a0a631a7-b8d8-4063-bfe5-3bf1bc6c9a2a.jpg
\.


--
-- Data for Name: recipe_categories; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.recipe_categories (category_id, recipe_id) FROM stdin;
84c1703a-8396-46f3-8a16-409c6eb54b49	7c998339-b710-4eb2-bcc2-2c971a776a9b
743f2bca-cd79-4816-8d3f-0409e5213d64	8c782ffd-4a57-4526-a730-0ecf8929997d
84c1703a-8396-46f3-8a16-409c6eb54b49	b962fac5-dbb5-4ed8-8a14-52ecc1edbe04
93e8f305-6ac4-45e2-a3de-24e17ed6caea	c8944e76-53c3-4d49-8189-7f403494e081
779fc600-3da9-4638-8cfc-67b1caa12eda	6aef7923-89cd-4e41-b54e-9d2e68a529eb
779fc600-3da9-4638-8cfc-67b1caa12eda	51b3259d-8146-4702-aef5-242b396f63a1
3a34372a-bbd4-490b-8930-32122ea92d19	53c252da-d0f2-4ae9-b5b3-71d014d07438
\.


--
-- Data for Name: recipe_ingredients; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.recipe_ingredients (quantity, id, ingredient_id, recipe_id, unit_id) FROM stdin;
500	a38394e3-faa8-4dc9-8fe3-b34cec446d42	5d178d9a-1999-4d14-be15-793b0829f165	7c998339-b710-4eb2-bcc2-2c971a776a9b	5cfa3982-af5b-4306-87c9-1b70133a621b
1	6f7247bc-4f6a-410a-8819-92224e4257af	94463598-2eb0-4af3-90cd-a35401114cc1	7c998339-b710-4eb2-bcc2-2c971a776a9b	d70072fb-5bfb-4582-99a6-adf0c498a9e0
4	51b7d31a-2f57-4815-9fa8-eb9abe5a21f8	13cff322-1d79-4891-be4f-ea6d7fe6b445	7c998339-b710-4eb2-bcc2-2c971a776a9b	784b74d9-84cc-42d9-8d8b-1ea314520ecc
2	564d561f-89dd-4577-bc7f-358b8e116b8a	c3547431-367c-4141-b704-383d28a988ee	7c998339-b710-4eb2-bcc2-2c971a776a9b	784b74d9-84cc-42d9-8d8b-1ea314520ecc
1	c04c376a-6c09-43ce-b2a9-45c1ff6801e4	63b0f6c6-17cd-41ca-bdcd-058cd79fb946	7c998339-b710-4eb2-bcc2-2c971a776a9b	9c31ad59-13d0-4f44-8037-f201ff49c16f
3	74ce3b5e-ad90-42f5-8137-7f45cd0c44e4	1fc2937e-8c7d-405d-a453-3077c5be3e60	7c998339-b710-4eb2-bcc2-2c971a776a9b	784b74d9-84cc-42d9-8d8b-1ea314520ecc
500	96642293-d08c-403b-a7d7-5cd61fac5cc9	13c4850f-0940-4d4e-adda-8da7298677aa	8c782ffd-4a57-4526-a730-0ecf8929997d	5cfa3982-af5b-4306-87c9-1b70133a621b
2	537f4271-28ca-434c-8ae7-f5e791cc828e	69ba1681-30a5-41c4-b3ae-caaa7222ce24	8c782ffd-4a57-4526-a730-0ecf8929997d	d70072fb-5bfb-4582-99a6-adf0c498a9e0
300	b14b4f33-48a3-4b7d-bd90-8ab8ccb1a0fa	bc4a6da3-01d6-40ea-af68-6cc2e53a6fdb	8c782ffd-4a57-4526-a730-0ecf8929997d	5cfa3982-af5b-4306-87c9-1b70133a621b
3	68feeaa7-381c-45d4-a66a-8e15288e3125	239b5014-092c-4dc0-b670-46a024506d63	8c782ffd-4a57-4526-a730-0ecf8929997d	d70072fb-5bfb-4582-99a6-adf0c498a9e0
1	2848c8b3-8548-4a5b-9806-e6bd5f07b489	09e1da31-542c-4efc-92b1-588ef6005f3e	8c782ffd-4a57-4526-a730-0ecf8929997d	d70072fb-5bfb-4582-99a6-adf0c498a9e0
1	bef11bb1-dd90-4f78-bba1-464dc51ff4e3	272ed040-4240-43c3-8bb4-082ae8017e40	8c782ffd-4a57-4526-a730-0ecf8929997d	d70072fb-5bfb-4582-99a6-adf0c498a9e0
2	d8e93626-00b0-4658-a080-17707c4e3516	a8dbb379-0155-468c-86c8-3f03d6fd2505	8c782ffd-4a57-4526-a730-0ecf8929997d	784b74d9-84cc-42d9-8d8b-1ea314520ecc
2	efc13107-0752-4ba4-b7de-8db528ba5bbe	677b90b8-dca6-48ac-bff0-64aa54bf1f33	8c782ffd-4a57-4526-a730-0ecf8929997d	d70072fb-5bfb-4582-99a6-adf0c498a9e0
2	eba15064-796b-4a58-b58a-8d7c4e8e7f07	4532ebaf-aebf-43d9-8e57-f3e0240f1b16	8c782ffd-4a57-4526-a730-0ecf8929997d	d70072fb-5bfb-4582-99a6-adf0c498a9e0
4	0ef4e456-3296-4c85-8879-953953f83247	edf13393-d18e-4c77-9adc-303acd925b11	8c782ffd-4a57-4526-a730-0ecf8929997d	784b74d9-84cc-42d9-8d8b-1ea314520ecc
500	ebb593cb-694e-406c-b02e-1a267f84c088	acaaa9d1-2345-420d-96da-424cd467f397	b962fac5-dbb5-4ed8-8a14-52ecc1edbe04	e2bda4b1-e519-461f-b644-0a17f2ea08aa
2	d6232268-8a77-41fa-b631-be57ab91e547	94463598-2eb0-4af3-90cd-a35401114cc1	b962fac5-dbb5-4ed8-8a14-52ecc1edbe04	d70072fb-5bfb-4582-99a6-adf0c498a9e0
200	b4b5ac10-24aa-43cb-849e-eb6b82e9db74	13cff322-1d79-4891-be4f-ea6d7fe6b445	b962fac5-dbb5-4ed8-8a14-52ecc1edbe04	5cfa3982-af5b-4306-87c9-1b70133a621b
2	19d055cc-6cf7-4b6f-8320-d6f5224ee9a4	c3547431-367c-4141-b704-383d28a988ee	b962fac5-dbb5-4ed8-8a14-52ecc1edbe04	784b74d9-84cc-42d9-8d8b-1ea314520ecc
1	c4027cb8-2726-46f5-9c3c-a773dc7731df	63b0f6c6-17cd-41ca-bdcd-058cd79fb946	b962fac5-dbb5-4ed8-8a14-52ecc1edbe04	9c31ad59-13d0-4f44-8037-f201ff49c16f
2	272ad39d-175f-4f98-a60a-51711ea826cd	1fc2937e-8c7d-405d-a453-3077c5be3e60	b962fac5-dbb5-4ed8-8a14-52ecc1edbe04	784b74d9-84cc-42d9-8d8b-1ea314520ecc
30	d5b0f7b8-fa5f-4099-be3b-6aad79458de2	86f619a9-19c8-441e-b062-b880c33a2891	b962fac5-dbb5-4ed8-8a14-52ecc1edbe04	5cfa3982-af5b-4306-87c9-1b70133a621b
4	06e51f9a-278e-44c5-a4e6-f58f5dc2c3f5	752bb249-2883-4939-9bfd-6854a1d65860	c8944e76-53c3-4d49-8189-7f403494e081	d70072fb-5bfb-4582-99a6-adf0c498a9e0
1	a8ce50e5-1b3b-4cab-a691-191bd9baf3c9	402a9200-5930-4f5e-b11e-75ab1afca62a	c8944e76-53c3-4d49-8189-7f403494e081	d70072fb-5bfb-4582-99a6-adf0c498a9e0
4	41091b59-2025-4732-9e7a-ea9800d795c3	28d8aed7-72f9-4321-8abc-738260fc31ae	c8944e76-53c3-4d49-8189-7f403494e081	d70072fb-5bfb-4582-99a6-adf0c498a9e0
300	19978a5c-9c32-4445-bec9-cf7194d5bc16	df9f57e1-3c43-42b1-a4f1-7911f17deac0	c8944e76-53c3-4d49-8189-7f403494e081	5cfa3982-af5b-4306-87c9-1b70133a621b
3	dcda3161-56ad-4d0e-b58d-120e5484b5d7	8e0c6f2d-3ca8-4d9a-a48c-f814bde2ef5f	c8944e76-53c3-4d49-8189-7f403494e081	d70072fb-5bfb-4582-99a6-adf0c498a9e0
1	5c013b7f-cad3-47ca-8b98-5d5fb62c354e	f5e0c44a-c60f-4c67-b823-6fd115249368	c8944e76-53c3-4d49-8189-7f403494e081	482802f4-4681-425a-84d5-561bf8e07b46
30	e8606637-ade9-4f98-9d4c-8965ac1edce3	0840f5b9-7b38-4d47-bd59-36863bd42192	c8944e76-53c3-4d49-8189-7f403494e081	5cfa3982-af5b-4306-87c9-1b70133a621b
200	956fe7eb-07f5-46bf-aead-a4309d7a8786	bebc4d52-e3ea-4d07-96b3-c9bbb6283db0	c8944e76-53c3-4d49-8189-7f403494e081	5cfa3982-af5b-4306-87c9-1b70133a621b
500	4688e361-4622-4539-a739-350ea565580e	13cff322-1d79-4891-be4f-ea6d7fe6b445	6aef7923-89cd-4e41-b54e-9d2e68a529eb	5cfa3982-af5b-4306-87c9-1b70133a621b
1	153a937a-e56d-47f5-9d09-31833085862f	94463598-2eb0-4af3-90cd-a35401114cc1	6aef7923-89cd-4e41-b54e-9d2e68a529eb	d70072fb-5bfb-4582-99a6-adf0c498a9e0
200	356ad018-fd70-4496-8bc5-819883952a04	83d75424-943f-48dd-b734-402db1b76be7	6aef7923-89cd-4e41-b54e-9d2e68a529eb	e2bda4b1-e519-461f-b644-0a17f2ea08aa
500	34cd3166-ea96-4f91-8f3b-f15898239752	69253823-149d-49b0-a073-180b4bf9064f	6aef7923-89cd-4e41-b54e-9d2e68a529eb	5cfa3982-af5b-4306-87c9-1b70133a621b
2	f44890ce-702e-4a2f-aeab-b65319cd39d2	272ed040-4240-43c3-8bb4-082ae8017e40	6aef7923-89cd-4e41-b54e-9d2e68a529eb	d70072fb-5bfb-4582-99a6-adf0c498a9e0
1	17eddcd3-6db9-4632-8f3e-cfb95e6cfae5	63b0f6c6-17cd-41ca-bdcd-058cd79fb946	6aef7923-89cd-4e41-b54e-9d2e68a529eb	fb95a96c-41fb-457a-a922-523e7a3f8390
1	32bc3a8e-2c66-4582-b921-a82d62757e7c	329568ad-e2a5-4566-aacc-748fbe77e4a7	6aef7923-89cd-4e41-b54e-9d2e68a529eb	9c31ad59-13d0-4f44-8037-f201ff49c16f
2	78424e01-7c9f-4afb-8398-05ade9f2ccc6	4532ebaf-aebf-43d9-8e57-f3e0240f1b16	6aef7923-89cd-4e41-b54e-9d2e68a529eb	d70072fb-5bfb-4582-99a6-adf0c498a9e0
1	e64fc052-517b-4341-bfbd-34af4289686c	bc4a6da3-01d6-40ea-af68-6cc2e53a6fdb	51b3259d-8146-4702-aef5-242b396f63a1	8d00f8e2-cd06-4c6f-8a12-87ff1c887464
500	6ffedbd6-2b1f-46ae-9374-2ff96e0e99d6	69253823-149d-49b0-a073-180b4bf9064f	51b3259d-8146-4702-aef5-242b396f63a1	5cfa3982-af5b-4306-87c9-1b70133a621b
150	8544e939-fa6b-48ae-ab6e-021a05593e68	5429a21c-55e2-460d-a256-e1875218c74a	51b3259d-8146-4702-aef5-242b396f63a1	5cfa3982-af5b-4306-87c9-1b70133a621b
1	93ade284-19b1-41be-946f-5971c8af6d3e	272ed040-4240-43c3-8bb4-082ae8017e40	51b3259d-8146-4702-aef5-242b396f63a1	d70072fb-5bfb-4582-99a6-adf0c498a9e0
1	d402bd67-fd5e-40dd-a324-9e42fddbb910	09e1da31-542c-4efc-92b1-588ef6005f3e	51b3259d-8146-4702-aef5-242b396f63a1	d70072fb-5bfb-4582-99a6-adf0c498a9e0
200	19baeaf1-3ea0-43a5-bdfc-bef286990443	edf13393-d18e-4c77-9adc-303acd925b11	51b3259d-8146-4702-aef5-242b396f63a1	5cfa3982-af5b-4306-87c9-1b70133a621b
2	856726a8-3eaa-46b3-aed3-d00c75f99adf	a8dbb379-0155-468c-86c8-3f03d6fd2505	51b3259d-8146-4702-aef5-242b396f63a1	784b74d9-84cc-42d9-8d8b-1ea314520ecc
1	58e51be6-60fc-4992-9ed1-23bf6ec4d5bb	63b0f6c6-17cd-41ca-bdcd-058cd79fb946	51b3259d-8146-4702-aef5-242b396f63a1	fb95a96c-41fb-457a-a922-523e7a3f8390
4	df10a8ff-6781-445f-9298-a3a865a4363c	c08ca014-98ce-4e9f-a4b9-e1b14a7000de	53c252da-d0f2-4ae9-b5b3-71d014d07438	d70072fb-5bfb-4582-99a6-adf0c498a9e0
4	ea786bca-28a1-4d26-8974-3c3796f7587b	94463598-2eb0-4af3-90cd-a35401114cc1	53c252da-d0f2-4ae9-b5b3-71d014d07438	d70072fb-5bfb-4582-99a6-adf0c498a9e0
1	6f5cedb4-7724-49fd-8050-33acf8281958	c3547431-367c-4141-b704-383d28a988ee	53c252da-d0f2-4ae9-b5b3-71d014d07438	c1ec0914-8074-469a-882b-e6d6b327864d
1	beb15c8c-0481-40fd-b183-68fbc733f8a1	13cff322-1d79-4891-be4f-ea6d7fe6b445	53c252da-d0f2-4ae9-b5b3-71d014d07438	c1ec0914-8074-469a-882b-e6d6b327864d
1	94ae93e9-27fa-4165-8a63-ae79f9272c75	77d4f463-f0e3-475c-9904-09204b3db6eb	53c252da-d0f2-4ae9-b5b3-71d014d07438	fb95a96c-41fb-457a-a922-523e7a3f8390
1	812918bb-cfec-43ad-9130-5d01063812bd	001e8e51-a6c2-4ec8-a4e2-d78b72a31179	53c252da-d0f2-4ae9-b5b3-71d014d07438	9c31ad59-13d0-4f44-8037-f201ff49c16f
10	90fb4158-c9f5-4896-b4c5-1ea463106423	86f619a9-19c8-441e-b062-b880c33a2891	53c252da-d0f2-4ae9-b5b3-71d014d07438	5cfa3982-af5b-4306-87c9-1b70133a621b
\.


--
-- Data for Name: recipe_notes; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.recipe_notes (created_at, id, recipe_id, user_id, note) FROM stdin;
\.


--
-- Data for Name: recipe_photos; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.recipe_photos (recipe_id, url) FROM stdin;
7c998339-b710-4eb2-bcc2-2c971a776a9b	/uploads/288917aa-b544-4f91-98c3-5afbaa5d9dc6.jpg
8c782ffd-4a57-4526-a730-0ecf8929997d	/uploads/397061e4-401e-4d64-b558-79318b52b9d6.jpg
b962fac5-dbb5-4ed8-8a14-52ecc1edbe04	/uploads/960cc1dc-b388-4e3a-9d1d-9a747165b6d4.jpg
c8944e76-53c3-4d49-8189-7f403494e081	/uploads/c263f2a0-f392-4986-9595-24ab3aba6046.jpg
6aef7923-89cd-4e41-b54e-9d2e68a529eb	/uploads/385602ab-0a19-4528-8173-333195aefa3b.jpg
51b3259d-8146-4702-aef5-242b396f63a1	/uploads/7b05df19-1dfa-4668-9a61-6bafed6c62cb.jpg
53c252da-d0f2-4ae9-b5b3-71d014d07438	/uploads/a0a631a7-b8d8-4063-bfe5-3bf1bc6c9a2a.jpg
\.


--
-- Data for Name: recipe_shared_users; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.recipe_shared_users (recipe_id, user_id) FROM stdin;
\.


--
-- Data for Name: recipe_trash; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.recipe_trash (deleted_at, recipe_id) FROM stdin;
\.


--
-- Data for Name: recipe_video_links; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.recipe_video_links (recipe_id, url) FROM stdin;
\.


--
-- Data for Name: recipes; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.recipes (is_public, is_trashed, publish_locked, created_at, updated_at, copied_from_id, id, user_id, visibility, description, name, removal_reason) FROM stdin;
t	f	f	2026-11-17 21:18:44.107424-08	2026-11-17 21:18:44.107429-08	\N	7c998339-b710-4eb2-bcc2-2c971a776a9b	6a2dd0c2-bfe3-42c6-ac41-817330a7a665	PUBLIC	Soft, golden pancakes made from farmer's cheese. Serve with sour cream or jam.\n\n1. Mash the cottage cheese with a fork or press it through a sieve.\n2. Add the egg, sugar and a pinch of salt and mix well.\n3. Stir in the flour to make a soft dough.\n4. Shape small patties and dust them with flour.\n5. Fry in oil over medium heat for 3-4 minutes per side until golden.	Syrniki (Cottage Cheese Pancakes)	\N
t	f	f	2026-11-17 21:18:44.39957-08	2026-11-17 21:18:44.399572-08	\N	8c782ffd-4a57-4526-a730-0ecf8929997d	6a2dd0c2-bfe3-42c6-ac41-817330a7a665	PUBLIC	Rich red beet soup, just like Mom made it. Even better the next day.\n\n1. Simmer the beef in water for about 1.5 hours to make a broth.\n2. Grate the beets and braise them with the tomato paste and a splash of vinegar.\n3. Add diced potatoes to the broth, then the shredded cabbage 10 minutes later.\n4. Fry the onion and carrot in oil and add them to the pot with the beets.\n5. Season with salt, add the bay leaves and garlic, and cook 5 more minutes.\n6. Let it rest for 20 minutes. Serve with sour cream and fresh dill.	Borscht	\N
f	f	f	2026-11-17 21:18:44.733024-08	2026-11-17 21:18:44.733028-08	\N	c8944e76-53c3-4d49-8189-7f403494e081	6a2dd0c2-bfe3-42c6-ac41-817330a7a665	ONLY_ME	The classic New Year's salad. My version uses chicken.\n\n1. Boil the potatoes, carrot and eggs, then cool and peel them.\n2. Dice everything finely together with the chicken and pickles.\n3. Add the green peas and finely chopped green onion.\n4. Dress with mayonnaise just before serving and salt to taste.	Olivier Salad	\N
f	f	f	2026-11-17 21:18:45.090357-08	2026-11-17 21:18:45.090361-08	\N	6aef7923-89cd-4e41-b54e-9d2e68a529eb	6a2dd0c2-bfe3-42c6-ac41-817330a7a665	ONLY_ME	Meat dumplings we make together as a family and freeze for later.\n\n1. Knead a dough from the flour, egg, cold water and salt. Cover and rest for 30 minutes.\n2. For the filling, mix the ground meat with finely chopped onion, salt, pepper and a little water.\n3. Roll the dough out thin and cut circles with a glass.\n4. Put filling on each circle, fold into a half-moon and pinch the corners together.\n5. Boil in salted water with a bay leaf for 5-7 minutes after they float.\n6. Serve with sour cream and herbs.	Pelmeni	\N
f	f	f	2026-11-17 21:18:45.298094-08	2026-11-17 21:18:45.298098-08	\N	51b3259d-8146-4702-aef5-242b396f63a1	6a2dd0c2-bfe3-42c6-ac41-817330a7a665	ONLY_ME	Golubtsy: cabbage rolls with meat and rice in a tomato and sour cream sauce.\n\n1. Blanch the cabbage head in boiling water and peel off the softened leaves. Trim the thick veins.\n2. Mix the ground meat with the rice, fried onion, salt and pepper.\n3. Wrap the filling in the leaves like little envelopes.\n4. Brown the rolls lightly and pack them tightly in a pot.\n5. Pour over a sauce of sour cream, tomato paste and water, cover and simmer for 40 minutes.	Stuffed Cabbage Rolls	\N
t	f	f	2026-11-17 21:18:45.506919-08	2026-11-17 21:18:45.506923-08	\N	53c252da-d0f2-4ae9-b5b3-71d014d07438	6a2dd0c2-bfe3-42c6-ac41-817330a7a665	PUBLIC	Sharlotka: the simplest apple cake there is, and it never fails.\n\n1. Heat the oven to 180 °C (350 °F) and butter a round pan.\n2. Core the apples, cut them into slices and sprinkle with lemon juice.\n3. Beat the eggs with the sugar for 5-7 minutes until pale and fluffy.\n4. Gently fold in the flour, baking powder and a pinch of cinnamon.\n5. Lay the apples in the pan and pour the batter over them.\n6. Bake for 40 minutes, until a skewer comes out clean.	Apple Charlotte	\N
t	f	f	2026-11-17 21:18:44.603441-08	2026-11-17 21:40:24.262488-08	\N	b962fac5-dbb5-4ed8-8a14-52ecc1edbe04	6a2dd0c2-bfe3-42c6-ac41-817330a7a665	PUBLIC	Thin, lacy crepes made with milk, the way Mom made them every Sunday morning.\n\n1. Whisk the eggs with the sugar and salt.\n2. Pour in half the milk, add the flour and whisk until smooth.\n3. Add the rest of the milk and the oil, then let the batter rest for 15 minutes.\n4. Heat a lightly oiled pan and fry each crepe on both sides.\n5. Brush every hot crepe with melted butter.	Blini	\N
\.


--
-- Data for Name: units; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.units (grams_per_unit, id, abbreviation, name, type) FROM stdin;
\N	5cfa3982-af5b-4306-87c9-1b70133a621b	g	gram	WEIGHT
\N	8d00f8e2-cd06-4c6f-8a12-87ff1c887464	kg	kilogram	WEIGHT
\N	c1ec0914-8074-469a-882b-e6d6b327864d	cup	cup	VOLUME
\N	784b74d9-84cc-42d9-8d8b-1ea314520ecc	tbsp	tablespoon	VOLUME
\N	fb95a96c-41fb-457a-a922-523e7a3f8390	tsp	teaspoon	VOLUME
\N	d70072fb-5bfb-4582-99a6-adf0c498a9e0	pcs	piece	COUNT
1	e2bda4b1-e519-461f-b644-0a17f2ea08aa	ml	milliliter	VOLUME
1000	e388075d-617e-46a6-9c81-cbc20562ccb4	l	liter	VOLUME
0.5	9c31ad59-13d0-4f44-8037-f201ff49c16f	pinch	pinch	OTHER
400	482802f4-4681-425a-84d5-561bf8e07b46	can	can	COUNT
\.


--
-- Data for Name: user_blocks; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.user_blocks (created_at, blocked_id, blocker_id, id) FROM stdin;
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.users (suspended, created_at, password_changed_at, terms_accepted_at, updated_at, id, email, password, preferred_locale, role, username) FROM stdin;
f	\N	\N	\N	\N	e672d0cc-1922-4539-beb3-3c63b9b40179	family@recipes.local	!no-password-set	ru	USER	family
f	2026-11-17 21:18:43.286659-08	\N	2026-11-17 21:18:43.265616-08	2026-11-17 21:18:43.286685-08	6a2dd0c2-bfe3-42c6-ac41-817330a7a665	demo@example.com	$2a$10$/wO5nx6pr69T5xpY7SQI.ucpeUTDXvS/2nX7/v4pAnr2Hu157sjRO	en	ROLE_USER	demo
\.


--
-- Name: blacklisted_tokens blacklisted_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blacklisted_tokens
    ADD CONSTRAINT blacklisted_tokens_pkey PRIMARY KEY (id);


--
-- Name: blacklisted_tokens blacklisted_tokens_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.blacklisted_tokens
    ADD CONSTRAINT blacklisted_tokens_token_key UNIQUE (token);


--
-- Name: categories categories_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_name_key UNIQUE (name);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


--
-- Name: content_reports content_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_reports
    ADD CONSTRAINT content_reports_pkey PRIMARY KEY (id);


--
-- Name: entity_translations entity_translations_entity_type_entity_id_field_name_locale_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.entity_translations
    ADD CONSTRAINT entity_translations_entity_type_entity_id_field_name_locale_key UNIQUE (entity_type, entity_id, field_name, locale);


--
-- Name: entity_translations entity_translations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.entity_translations
    ADD CONSTRAINT entity_translations_pkey PRIMARY KEY (id);


--
-- Name: ingredient_unit_weights ingredient_unit_weights_ingredient_id_unit_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ingredient_unit_weights
    ADD CONSTRAINT ingredient_unit_weights_ingredient_id_unit_id_key UNIQUE (ingredient_id, unit_id);


--
-- Name: ingredient_unit_weights ingredient_unit_weights_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ingredient_unit_weights
    ADD CONSTRAINT ingredient_unit_weights_pkey PRIMARY KEY (id);


--
-- Name: ingredients ingredients_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ingredients
    ADD CONSTRAINT ingredients_name_key UNIQUE (name);


--
-- Name: ingredients ingredients_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ingredients
    ADD CONSTRAINT ingredients_pkey PRIMARY KEY (id);


--
-- Name: password_reset_tokens password_reset_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_pkey PRIMARY KEY (id);


--
-- Name: password_reset_tokens password_reset_tokens_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_token_key UNIQUE (token);


--
-- Name: photo_uploads photo_uploads_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.photo_uploads
    ADD CONSTRAINT photo_uploads_pkey PRIMARY KEY (file_name);


--
-- Name: recipe_categories recipe_categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipe_categories
    ADD CONSTRAINT recipe_categories_pkey PRIMARY KEY (recipe_id, category_id);


--
-- Name: recipe_ingredients recipe_ingredients_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipe_ingredients
    ADD CONSTRAINT recipe_ingredients_pkey PRIMARY KEY (id);


--
-- Name: recipe_notes recipe_notes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipe_notes
    ADD CONSTRAINT recipe_notes_pkey PRIMARY KEY (id);


--
-- Name: recipe_shared_users recipe_shared_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipe_shared_users
    ADD CONSTRAINT recipe_shared_users_pkey PRIMARY KEY (recipe_id, user_id);


--
-- Name: recipe_trash recipe_trash_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipe_trash
    ADD CONSTRAINT recipe_trash_pkey PRIMARY KEY (recipe_id);


--
-- Name: recipes recipes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipes
    ADD CONSTRAINT recipes_pkey PRIMARY KEY (id);


--
-- Name: units units_abbreviation_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.units
    ADD CONSTRAINT units_abbreviation_key UNIQUE (abbreviation);


--
-- Name: units units_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.units
    ADD CONSTRAINT units_pkey PRIMARY KEY (id);


--
-- Name: user_blocks user_blocks_blocker_id_blocked_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_blocks
    ADD CONSTRAINT user_blocks_blocker_id_blocked_id_key UNIQUE (blocker_id, blocked_id);


--
-- Name: user_blocks user_blocks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_blocks
    ADD CONSTRAINT user_blocks_pkey PRIMARY KEY (id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- Name: idx_photo_uploads_uploader; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_photo_uploads_uploader ON public.photo_uploads USING btree (uploader_id);


--
-- Name: recipe_categories fk3w4m6a9qnpwjgknvss7amxhjd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipe_categories
    ADD CONSTRAINT fk3w4m6a9qnpwjgknvss7amxhjd FOREIGN KEY (recipe_id) REFERENCES public.recipes(id);


--
-- Name: content_reports fk40bn3nq9t2qk66fkm6c3qwvjq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.content_reports
    ADD CONSTRAINT fk40bn3nq9t2qk66fkm6c3qwvjq FOREIGN KEY (reporter_id) REFERENCES public.users(id);


--
-- Name: user_blocks fk7k3mfgb03bnmwh81vqb1u5h80; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_blocks
    ADD CONSTRAINT fk7k3mfgb03bnmwh81vqb1u5h80 FOREIGN KEY (blocked_id) REFERENCES public.users(id);


--
-- Name: recipe_ingredients fk9bk1tkxfum0ufh3foxug8yahm; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipe_ingredients
    ADD CONSTRAINT fk9bk1tkxfum0ufh3foxug8yahm FOREIGN KEY (unit_id) REFERENCES public.units(id);


--
-- Name: categories fk9il7y6fehxwunjeepq0n7g5rd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT fk9il7y6fehxwunjeepq0n7g5rd FOREIGN KEY (parent_category_id) REFERENCES public.categories(id);


--
-- Name: recipe_notes fkapb1t6lujtiov3k6tpjtlt9l; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipe_notes
    ADD CONSTRAINT fkapb1t6lujtiov3k6tpjtlt9l FOREIGN KEY (recipe_id) REFERENCES public.recipes(id);


--
-- Name: recipe_notes fkbsn4mn15f9berc39lrcaq8wbj; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipe_notes
    ADD CONSTRAINT fkbsn4mn15f9berc39lrcaq8wbj FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: recipe_ingredients fkcqlw8sor5ut10xsuj3jnttkc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipe_ingredients
    ADD CONSTRAINT fkcqlw8sor5ut10xsuj3jnttkc FOREIGN KEY (recipe_id) REFERENCES public.recipes(id);


--
-- Name: recipe_ingredients fkgukrw6na9f61kb8djkkuvyxy8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipe_ingredients
    ADD CONSTRAINT fkgukrw6na9f61kb8djkkuvyxy8 FOREIGN KEY (ingredient_id) REFERENCES public.ingredients(id);


--
-- Name: user_blocks fkgvu85oyjrfafwttb7iphgmm0v; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_blocks
    ADD CONSTRAINT fkgvu85oyjrfafwttb7iphgmm0v FOREIGN KEY (blocker_id) REFERENCES public.users(id);


--
-- Name: recipe_shared_users fkh7yrbubd9pjmwkiygxdyb0dw2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipe_shared_users
    ADD CONSTRAINT fkh7yrbubd9pjmwkiygxdyb0dw2 FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: recipe_photos fkjfgeic8pppamr1ltc39h6t4a8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipe_photos
    ADD CONSTRAINT fkjfgeic8pppamr1ltc39h6t4a8 FOREIGN KEY (recipe_id) REFERENCES public.recipes(id);


--
-- Name: password_reset_tokens fkk3ndxg5xp6v7wd4gjyusp15gq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT fkk3ndxg5xp6v7wd4gjyusp15gq FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: recipe_categories fkl4gklbf4tpxuk41fp77pgd28l; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipe_categories
    ADD CONSTRAINT fkl4gklbf4tpxuk41fp77pgd28l FOREIGN KEY (category_id) REFERENCES public.categories(id);


--
-- Name: recipes fklc3x6yty3xsupx80hqbj9ayos; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipes
    ADD CONSTRAINT fklc3x6yty3xsupx80hqbj9ayos FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: ingredient_unit_weights fkn7avrfq9oqjunxqi3dsebwpjd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ingredient_unit_weights
    ADD CONSTRAINT fkn7avrfq9oqjunxqi3dsebwpjd FOREIGN KEY (ingredient_id) REFERENCES public.ingredients(id);


--
-- Name: recipe_video_links fkq8jrcombismmwfr7kgxd44vrw; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipe_video_links
    ADD CONSTRAINT fkq8jrcombismmwfr7kgxd44vrw FOREIGN KEY (recipe_id) REFERENCES public.recipes(id);


--
-- Name: recipe_shared_users fkqkccreagnvee91a3yvr2r99xy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipe_shared_users
    ADD CONSTRAINT fkqkccreagnvee91a3yvr2r99xy FOREIGN KEY (recipe_id) REFERENCES public.recipes(id);


--
-- Name: ingredient_unit_weights fkt3dpexgkgmllynq5imcmqlws1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ingredient_unit_weights
    ADD CONSTRAINT fkt3dpexgkgmllynq5imcmqlws1 FOREIGN KEY (unit_id) REFERENCES public.units(id);


--
-- PostgreSQL database dump complete
--


