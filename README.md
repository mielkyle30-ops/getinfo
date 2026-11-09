# getinfo — Cisco switch data collector

A small Java tool that logs in to every switch in a list, runs the same set of read-only `show` commands, and saves the results by date, so config backups and topology data stay current across the whole fleet.

## What it does

- Reads a device list, sorts it by IP, and drops duplicate lines.
- **Pre-pings** every host in parallel (10 at a time) and skips unreachable devices before connecting.
- Connects to **5 switches at a time** over Telnet (Apache Commons Net) and handles four login flows: line password, username/password, `login:` prompt, and router-style login with enable.
- Sends `terminal length 0`, then runs 14 read-only commands per device:
  `show clock`, `show ntp status`, `show version`, `show ip interface brief`, `show interfaces status`, `show cdp neighbors`, `show cdp neighbors detail`, `show ip arp`, `show mac address-table`, `show vlan`, `show processes`, `show ip route`, `show run`, `show log`.
- Saves each output as `<ip>_<date>_<command>.txt` in a dated run folder.
- Writes **one summary row per switch** (`<HH-mm>_List.csv`): hostname, serial number, IOS version, NTP sync, unused ports / total ports, CDP neighbors, and login type.
- Parses `show cdp neighbors detail` into a CSV topology map (`<date>_CDP_detail.csv`: Device ID, IP, platform, local and remote port).
- Retries failed switches once, then lists any that still fail in `log.txt`.
- Zips the day's `.txt` outputs into `Switch_log.zip` and removes the loose files.

The tool is **read-only by design**: it only runs `show` commands and never changes a device.

## Files

| File | Role |
|---|---|
| `src/Ciscosetting.java` | Main: device list, pre-ping, login flows, batch command runner, output files |
| `src/CollectorSwitchData.java` | Per-switch data model and parsers (hostname, version, serial, NTP, ports) |
| `src/CDPParser.java` | Parses CDP neighbor detail into a CSV topology map |
| `src/DeviceInfo.java` | CDP neighbor record |
| `src/ZipTxtFiles.java` | Archives the run's `.txt` outputs |
| `lib/commons-net-3.9.0.jar` | Apache Commons Net (Telnet client), Apache License 2.0 |

## Build

Requires a JDK (11 or newer).

```bat
javac -encoding UTF-8 -cp lib\commons-net-3.9.0.jar -d bin src\*.java
echo Main-Class: Ciscosetting> manifest.txt
echo Class-Path: lib/commons-net-3.9.0.jar>> manifest.txt
jar cfm getinfo.jar manifest.txt -C bin .
```

## Run

1. Copy `switchlist.example.txt` to `switchlist.txt` and fill in your devices. **Never commit the real list.**
2. Run from the folder that contains `lib\`:

```bat
java -jar getinfo.jar                 :: uses .\switchlist.txt
java -jar getinfo.jar sitelist.txt    :: uses another list file
```

Results go to `.\cisco\<yyyy-MM-dd> <listname>\`.

### Device list format

One device per line, comma-separated. Lines starting with `#` are ignored.

```
<ip>,local,<line-password>,<enable-password>
<ip>,router,<username>,<password>,<enable-password>
<ip>,radious,<username>,<password>
<ip>,core,<username>,<password>
```

(`radious` is spelled the way the code expects it.)

## Security notes

- **Telnet is plain text.** Passwords and output, including `show run`, cross the network unencrypted. Use it only on an isolated management network, with vty access limited by ACL to the admin host.
- **The device list holds credentials in plain text.** Keep it out of Git (see `.gitignore`), and limit who can read it on the admin PC.
- **Outputs can contain secrets.** `show run` may include SNMP communities and password hashes. Store the run folders and `Switch_log.zip` in a restricted location.
- The final failed-device list in `log.txt` records only the IP and login type, never passwords.

## Roadmap

- SSH support (for example JSch), trying SSH first and falling back to Telnet per device.
- Credentials from a vault or prompted at run time instead of a file.
- Mask secret lines in `show run` before saving, and encrypt the archive.
- Config checks against a baseline (SSH only, NTP and SNMPv3 set, BPDU guard on access ports) to flag drift.

## Why I built it

As the sole on-site network engineer for about 190 switches, I automated the repetitive CLI work so config backups and topology data stay current across the whole fleet, which frees up time for actual troubleshooting. Changes are still made by hand; this tool only collects.
