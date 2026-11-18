# Panduan Bahasa Indonesia

Dokumen ini adalah jalur tercepat untuk pembaca yang ingin memahami apa yang
dibuktikan oleh repository, menjalankan Docker lab, dan membaca hasilnya tanpa
menganggap setiap error sebagai shell.

## Apa yang dibuktikan

Repository membandingkan dua container dari image JDK yang sama:

- `vulnerable` memakai Base64 intrinsic bawaan;
- `mitigated` menambahkan `-XX:-UseBASE64Intrinsics`.

Hasil yang diharapkan pada host yang memenuhi syarat:

```text
vulnerable: changed_past_expected=62
mitigated:  changed_past_expected=0
```

Artinya, container rentan mengubah 62 byte setelah output Base64 yang
seharusnya, sedangkan flag mitigasi menghentikan perubahan tersebut.

## Syarat host

- Linux amd64 atau WSL2;
- Docker Engine dan plugin Compose;
- Bash dan `curl`;
- flag CPU `avx512f`, `avx512bw`, `avx512vbmi`, dan `bmi2`.

Docker tidak mengemulasikan AVX-512. Jika CPU atau VM tidak mengekspos flag
tersebut, image yang rentan sekalipun dapat menghasilkan nol perubahan.

## Langkah pertama

```bash
git clone https://github.com/zake1god/java2shell.git
cd java2shell
make preflight
```

Jika siap, output akan berakhir dengan:

```text
preflight_status=ready
next_step=make verify
```

## Jalankan verifikasi lengkap

```bash
make verify
```

Perintah ini membangun image, menjalankan dua container pada loopback, mengirim
trigger yang sama, menampilkan perbandingan, lalu membersihkan container.

Output utama:

```text
[vulnerable container]
{"changed_past_expected":62,"first_changed":50,"last_changed":111,...}
[mitigated control]
{"changed_past_expected":0,"first_changed":-1,"last_changed":-1,...}
```

Kedua sisi biasanya menampilkan `decoder_rejected=true`. Java memang menolak
input Base64 yang malformed, tetapi pada sisi rentan native intrinsic sudah
menulis memori sebelum exception dilempar.

## Arti hasil akhir

```text
verification_complete=true
evidence_level=bounded_oob_write_with_negative_control
shell_proven=false
```

`shell_proven=false` bukan berarti lab gagal. Bukti publik yang direproduksi
adalah out-of-bounds write 62 byte dan kontrol mitigasi nol byte.

RCE aplikasi-spesifik sudah didemonstrasikan oleh Calif, termasuk terhadap
Keycloak. Namun source code chain produk tersebut masih ditahan. Repository ini
tidak menambahkan endpoint command buatan, `ProcessBuilder`, role bit sintetis,
atau SSTI terpisah hanya agar terminal menampilkan `id`.

## Memeriksa Java pada host

```bash
make check
echo $?
```

Exit code:

| Kode | Arti |
| ---: | --- |
| `0` | Perilaku rentan tidak direproduksi |
| `1` | Java tidak tersedia atau checker gagal |
| `2` | Write setelah output berhasil direproduksi |

Kode `2` adalah hasil positif untuk perilaku rentan, bukan crash checker.

## Jika hasilnya nol pada kedua container

Jalankan:

```bash
uname -m
grep -m1 '^flags' /proc/cpuinfo | tr ' ' '\n' \
  | grep -E '^(avx512f|avx512bw|avx512vbmi|bmi2)$'
```

Pastikan empat flag muncul. Jika tidak, gunakan host amd64 lain yang kompatibel.
Jangan menghapus gate atau mengubah expected output supaya demo terlihat lolos.

## Jika melihat klaim shell dari tool lain

Periksa source code yang menentukan nilai `shell=1`. Beberapa tool publik
menguji endpoint `cmd`, `exec`, atau callback yang terpisah dari Base64 OOB.
Tool lain memakai aplikasi lab yang sengaja menjalankan `/usr/bin/id` ketika
role bit berubah. Keduanya bukan chain shell produk nyata.

Bukti shell yang sah harus menunjukkan command target disebabkan oleh rantai
CVE, gagal pada kontrol mitigasi, dan tidak memakai command gate yang sudah
ditanam di aplikasi.

## Bantuan lanjutan

- [Getting started](GETTING-STARTED.md)
- [Troubleshooting](TROUBLESHOOTING.md)
- [Technical notes](TECHNICAL.md)
- [RCE and shell status](RCE-STATUS.md)
- [Public exploit tracker](PUBLIC-EXPLOIT-TRACKER.md)

Gunakan hanya pada sistem milik sendiri atau yang memberikan izin tertulis.
