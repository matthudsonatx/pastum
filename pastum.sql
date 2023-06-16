create table pastum
(
  id         integer generated always as identity,
  created_on timestamp with time zone default CURRENT_TIMESTAMP not null,
  src_host   inet                                               not null,
  webkey     varchar(8)                                         not null
    unique,
  pastum     text                                               not null
);
