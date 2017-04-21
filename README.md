Dev Environment Setup
=====================
The purpose of this document is to cover all the steps required to bring up Dev Environment infrastructure on keas aws us-east-1 region. The VPC and Security Groups[Firewall] are shared for both Dev and Stage Environments. Frontend, Biorecs, and Nest DBs are hosted in the same RDS for both Dev and Stage Environments.

## Contents
* [Pre-Requisites](#pre-requisites)
* [Setup VPC](#setup-vpc)
* [Setup Security Group](#setup-security-group)
* [Setup Bastion Server](#setup-bastion-server)
* [MySQL Database RDS](#mysql-database-rds)
* [PostgreSQL Database RDS](#postgresql-database-rds)
* [DeployServer](#deployserver)
* [RabbitMQ](#rabbitmq)
* [Redis](#redis)
* [Resque](#resque)
* [Memcached](#memcached)
* [Frontend](#frontend)
* [Biorecs](#biorecs)
* [Nest](#nest)
* [Ratatoskr](#ratatoskr)
* [Haproxy](#haproxy)
* [Nagios](#nagios)
* [Splunk](#splunk)
* [Connection Details](#connection-details)
* [VPN Setup](#vpn-setup)

## Pre-Requisites
1. Access to [Clouformation Repo](https://github.com/welltok/CloudFormation/tree/keas-aws-migration) and [Ansible Repo](https://github.com/welltok/ansible-playbooks/tree/keas-aws-migration)
2. Should have aws cli installed and configured to access keas/welltok aws account
3. Create keas aws cli profile. Your ~/.aws/credentials should look like:
```bash
[keas]
aws_access_key_id = <your access key>
aws_secret_access_key = <your secret key>
```
4. Create a file ~/.ssh/vault.password with the ansible-vault password
5. Place bastion ssh key file under the path ~/.ssh/stagedev-welltokkeas-bastion.key
6. Place the ubuntu user ssh key file under the path ~/.ssh/ec2deploykeasstagedev.key
7. Install Ansible 2.2.1
8. Edit /etc/ansible/ansible.cfg to add these lines
```bash
[default]
sudo_flags=-H -i
allow_world_readable_tmpfiles = True
```
9. Setup ansible on your host based on : [Ansible and Dynamic Amazon EC2 Inventory Management](https://aws.amazon.com/blogs/apn/getting-started-with-ansible-and-dynamic-amazon-ec2-inventory-management/)
10. In /etc/ansible/ec2.ini make sure that the below two are defined so that the dynamic host inventory works fine:
```bash
destination_variable = public_dns_name
vpc_destination_variable = private_ip_address
```

## Setup VPC
#### Create StageDev VPC [Cloudformation Repo]
```bash
cd keas-infrastructure/vpc
sh run-stagedev.sh
sh create-stagedev.sh
```

## Setup Security Group
#### Create Security Group [Cloudformation Repo]
```bash
cd keas-infrastructure/security-group
sh run-stagedev.sh
sh create-stagedev.sh
```
Approx runtime: less than 3 mins

**IMPORTANT**
Once VPC is setup, Update Keas team with NAT Gateway Public IP available on [VPC web-console](https://console.aws.amazon.com/vpc/home?region=us-east-1#NatGateways:sort=desc:createTime). This IP needs to be whitelisted for the SILVERPOP mailing to work on Dev and Staging Environments.

## Setup Bastion Server
#### Create bastion Server [Cloudformation Repo]
```bash
cd keas-infrastructure/ec2/stage/bastion
sh run.sh
sh create.sh
```
Approx runtime: less than 3 mins

#### Create Bastion ssh users [Ansible Repo]
1. Run the following command to get the public ip for bastion host
```bash
aws ec2 describe-instances --region us-east-1 --filters Name=tag-value,Values=bastion \
--query 'Reservations[].Instances[].[Tags[?Key==`Name`].Value | [0], InstanceId, Placement.AvailabilityZone, InstanceType, PublicIpAddress, LaunchTime, State.Name]' \
--output table
---------------------------------------------------------------------------------------------------------------------------------------
|                                                          DescribeInstances                                                          |
+------------------------+----------------------+-------------+------------+-----------------+----------------------------+-----------+
|  BastionServer-StageDev|  i-0f8c87370c7501589 |  us-east-1b |  t2.medium |  54.157.199.215 |  2017-03-29T21:59:10.000Z  |  running  |
+------------------------+----------------------+-------------+------------+-----------------+----------------------------+-----------+
```
2. Update /etc/ansible/hosts with [bastion-stagedev] public ip  
3. cd keas-infrastructure  
4. Run the ansble play to add ssh users
```bash
time ansible-playbook -i /etc/ansible/hosts role-add-ssh-users.yml -e "targethosts=bastion-stagedev" --private-key=~/.ssh/stagedev-welltokkeas-bastion.key
```
Approx runtime: less than 2 mins
5. Add SSH Proxy for ansible to connect to instances within the VPC through Bastion  
Sample ~/.ssh/config file show below
```bash
Host 10.220.*.*
  User ubuntu
  IdentityFile ~/.ssh/ec2deploykeasprod.key
  StrictHostKeyChecking no
  ForwardAgent yes
  ProxyCommand ssh -W %h:%p mcheriyath@54.227.177.178 -i ~/.ssh/mcheriyath-aws-keas

Host 10.57.*.*
  User ubuntu
  IdentityFile ~/.ssh/ec2deploykeasstagedev.key
  StrictHostKeyChecking no
  ForwardAgent yes
  ProxyCommand ssh -W %h:%p mcheriyath@54.227.177.178 -i ~/.ssh/mcheriyath-aws-keas
```

## MySQL Database RDS
#### Launch MySQL RDS [Cloudformation Repo]
```bash
cd keas-infrastructure/rds/mysql-rds
sh run-rds-stagedev.sh
sh create-rds-stagedev.sh
```
Approx runtime: less than 15 mins

#### Create RDS DB, Users and Privileges [Ansible Repo]

**To create mysql db and users**
```bash
cd keas-infrastructure
time ansible-playbook -i /etc/ansible/hosts role-db-users-role.yml -e "targethost=bastion-stagedev" -e "remoteuser=ubuntu" -e "env=stagedev" --vault-password-file=~/.ssh/vault.password --private-key=~/.ssh/stagedev-welltokkeas-bastion.key --tags all
```
Approx runtime: less than 2 mins

#### Import DB
**Fetch files from S3 bucket**
1. Get the object names from the db-migration-upload s3 bucket with the following command.
```bash
aws s3 ls --human-readable s3://db-migration-upload/
2017-03-24 18:15:17   98.7 MiB biorecs_dev1-dev-0322.sql.gz
2017-03-24 17:59:41    1.8 GiB biorecs_prod1-prod-0322.sql.gz
2017-03-24 18:15:50   49.3 MiB biorecs_stage1-staging-0322.sql.gz
2017-03-24 18:13:09    1.3 GiB frontend_dev1-dev-0322.sql.gz
2017-03-24 17:39:07   14.7 GiB frontend_production_2017-03-22.sql.gz
2017-03-24 18:14:16    1.3 GiB frontend_stage1-staging-0324.sql.gz
2017-03-24 17:29:51    2.1 GiB ratatoskr_dev1-0322.sql.gz
2017-03-24 17:40:38   16.8 GiB ratatoskr_prod1-0322.sql.gz
2017-03-20 18:17:24    7.0 KiB reportingusers-dwdev1.sql
2017-03-20 18:19:12    8.1 KiB reportingusers-prod.sql
2017-03-17 17:47:36   11.2 KiB user_table_dump-prod.sql
```
2. Edit the [keas-infrastructure/roles/db-users-roles/defaults/main.yml](https://github.com/welltok/ansible-playbooks/blob/keas-aws-migration/keas-infrastructure/roles/db-users-roles/defaults/main.yml#L12) with the s3 object name  
3. Run the following command to copy the files onto bastion host
```bash
time ansible-playbook -i /etc/ansible/hosts role-db-users-role.yml -e "targethost=bastion-stagedev" -e "remoteuser=ubuntu" -e "env=stagedev" --vault-password-file=~/.ssh/vault.password --private-key=~/.ssh/stagedev-welltokkeas-bastion.key --tags s3filecopy
```
**Note: AWS_ACCESS_KEY and AWS_SECRET_KEY needs to be set as env variables on your local box**
Approx runtime: Varies on s3 file size. Production db copy from s3 to bastion can take upto 40 mins.  
4. Run the following command to create the DB and DB users
```bash
time ansible-playbook -i /etc/ansible/hosts role-db-users-role.yml -e "targethost=bastion-stagedev" -e "remoteuser=ubuntu" -e "env=stagedev" --vault-password-file=~/.ssh/vault.password --private-key=~/.ssh/stagedev-welltokkeas-bastion.key
```

**Import Frontend, Nest and Biorecs DB to RDS [Bastion Shell Access]**
```bash
ssh -i ~/.ssh/stagedev-welltokkeas-bastion.key ubuntu@54.197.181.25
cd ~/db-backup-from-s3
screen
time gzip -dc < frontend_stage1-staging-0324.sql.gz | mysql -h keasdb-stagedev-master.rds.keas.com -u frontend_staging -p --ssl-ca=/etc/rds/ssl/rds-combined-ca-bundle.pem frontend_stage
time gzip -dc < biorecs_stage1-staging-0322.sql.gz | mysql -h keasdb-stagedev-master.rds.keas.com -u biorecs_staging -p --ssl-ca=/etc/rds/ssl/rds-combined-ca-bundle.pem biorecs_stage
ubuntu@bastionserver-stagedev:~/db-backup-from-s3$ time gzip -dc frontend_dev1-dev-0412.sql.gz | mysql -h keasdb-stagedev-master.rds.keas.com -u frontend_dev -p --ssl-ca=/etc/rds/ssl/rds-combined-ca-bundle.pem frontend_dev
Enter password:

real    37m52.045s
user    4m27.516s
sys     0m12.584s
ubuntu@bastionserver-stagedev:~/db-backup-from-s3$ time gzip -dc biorecs_dev1-dev-0412.sql.gz | mysql -h keasdb-stagedev-master.rds.keas.com -u biorecs_dev -p --ssl-ca=/etc/rds/ssl/rds-combined-ca-bundle.pem biorecs_dev
Enter password:

real    0m48.696s
user    0m7.145s
sys     0m0.305s
```
Approx runtime: less than 40 mins

**Note:** You should use multiple [screens](http://www.tecmint.com/screen-command-examples-to-manage-linux-terminals/) to run this db import in the background.

## PostgreSQL Database RDS
#### Configuring Reporting DB
**Granting users roles [From Bastion Shell]**  
For proper importing of users and roles on reporting db we need to edit the user backup file available on s3.  
eg: reportingusers-dwdev1.  
1. Remove all the ALTER ROLE section and append the same to CREATE ROLE  
From
```bash
CREATE ROLE cafewell_etl;
ALTER ROLE cafewell_etl WITH NOSUPERUSER INHERIT NOCREATEROLE NOCREATEDB LOGIN NOREPLICATION PASSWORD 'md5restofthestring';
```
To
```bash
CREATE ROLE cafewell_etl WITH NOSUPERUSER INHERIT NOCREATEROLE NOCREATEDB LOGIN NOREPLICATION PASSWORD 'md5restofthestring';
```

An already converted file can be found in the [db-users-roles](https://github.com/welltok/ansible-playbooks/tree/keas-aws-migration/keas-infrastructure/roles/db-users-roles/files) ansible role  
Once this edit is completed we can import the users and roles with the below mentioned command

```bash
psql -f reportingusers-dwdev1.sql -h ratatoskr-dev.rds.keas.com --username Administrator -W postgres
```
Approx runtime: 1 min  

Note: [Password file](https://github.com/welltok/CloudFormation/blob/keas-aws-migration/keas-infrastructure/rds/postgres-rds/.dev.dbpassword.keasencrypted)

**To decrypt the file, run the below command:**
```
ansible-vault decrypt --output=/tmp/reportingdb.sql roles/db-users-roles/files/reportingusers-dwdev1.sql --vault-password-file=~/.ssh/vault.password
Decryption successful
```

**Create reporting dev db**
```bash
ubuntu@bastionserver:~$ psql -h ratatoskr-dev.rds.keas.com -U ratatoskr -W postgres
Password for user ratatoskr:
psql (9.3.16, server 9.3.14)
SSL connection (cipher: DHE-RSA-AES256-GCM-SHA384, bits: 256)
Type "help" for help.

postgres=>create database ratatoskr_dev1;
CREATE DATABASE
postgres=>
```
Note: [Password file](https://github.com/welltok/ansible-playbooks/blob/keas-aws-migration/keas-infrastructure/roles/krubyapp/files/ratatoskr-dev-vars-encrypted.sh.j2)

**Importing DB [From Bastion shell]**  
After successful creation of users and roles we can run the below commands to import the DB.
```bash
ubuntu@bastionserver-stagedev:~$cd ~/db-backup-from-s3
ubuntu@bastionserver-stagedev:~/db-backup-from-s3$time gunzip -c ratatoskr_dev1-0412.sql.gz | psql -q -h ratatoskr-dev.rds.keas.com --username ratatoskr -W --dbname ratatoskr_dev1
Password for user ratatoskr:
real    34m56.539s
user    2m15.933s
sys     0m15.684s
```
Approx runtime : 34 mins

## DeployServer
#### Launch Deploy Server [Cloudformation Repo]
```bash
cd keas-infrastructure/ec2/dev/deployserver
sh run.sh
sh create.sh
```

#### Configure Deploy Server [Ansible Repo]
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-deployer.yml -e "targethost=tag_Name_deployserverstagedev" -e "remoteuser=ubuntu" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=~/.ssh/vault.password
```
Repos are created manually over the web and available as:  
**Frontend:** https://$GITOLITE_USER:$GITOLITE_PASSWORD@deployserver-stagedev.keas.com/git/frontend.git  
**Biorecs:** https://$GITOLITE_USER:$GITOLITE_PASSWORD@deployserver-stagedev.keas.com/git/biorecs.git  
**Nest:** https://$GITOLITE_USER:$GITOLITE_PASSWORD@deployserver-stagedev.keas.com/git/nest.git  
**Ratatoskr:** https://$GITOLITE_USER:$GITOLITE_PASSWORD@deployserver-stagedev.keas.com/git/ratatoskr.git  

## RabbitMQ
#### Launch RabbitMQ Servers [Cloudformation Repo]

```bash
cd keas-infrastructure/ec2/dev/rabbitmq
sh run.sh
sh create.sh
```

#### Configure Rabbitmq Cluster [Ansible Repo]

```bash
time ansible-playbook -i /etc/ansible/ec2.py role-rabbitmq-app.yml -e "targethost=tag_aws_cloudformation_stack_name_keas_rabbitmq_dev" -e "remoteuser=ubuntu" -e "rabbitmq_app_environment=dev" -e "rabbitmq_master=rabbitmq1-dev" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=~/.ssh/vault.password
```

## Redis
#### Launch Redis-BFN Servers [Cloudformation Repo]

```bash
cd keas-infrastructure/ec2/dev/redis-bfn
sh run.sh
sh create.sh
```

#### Configure Redis Sentinel Cluster [Ansible Repo]

1. cd keas-infrastructure  
2. Update /etc/ansible/hosts with Redis Dev Environment IPs as per the [HowTo.Doc](https://github.com/welltok/ansible-playbooks/blob/keas-aws-migration/keas-infrastructure/roles/DavidWittman.redis/HOWTO.md)  
3. Edit role-redis.yml manually with the master redis IP  
4. Edit the redis_sentinel_monitors name with "dev1"  
5. Configure Sentinel with the following command
```bash
time ansible-playbook -i /etc/ansible/hosts role-redis.yml -e "remoteuser=ubuntu" --private-key=~/.ssh/ec2deploykeasstagedev.key
```
6. Comment the Sentinel portion in role-redis.yml  
7. Re-run the same command mentioned above in step 5  

#### Restore Redis DB from Rackspace
[How to backup and import redis db](http://zdk.blinkenshell.org/redis-backup-and-restore/)
1. Current Redis Rackspace Details
```bash
redisdev1.keas.com,redisdev2.keas.com,redisdev3.keas.com (Have Sentinel setup)
Used by :
keas-biorecs-dev1, 
keas-frontend-dev1,
keas-nest-dev1
```
2. Login to redisdev3.keas.com(master) and take the backup
```bash
[mcheriyath@695927-redisdev3 ~]$ redis-cli -h redisdev3.keas.com --rdb redis-dev-rackspacebackup.rdb
SYNC sent to master, writing 6418307 bytes to 'redis-dev-rackspacebackup.rdb'
Transfer finished with success.
```
3. Copy the file onto redis1-dev.keas.com(Master) /var/lib/redis/6379 <br>
4. Stop redis service running on slaves first and then on master
```bash
sudo service redis_6379 stop
```
5. On Redis Master replace /var/lib/redis/6379/dump.rds with /var/lib/redis/6379/redis-dev-rackspacebackup.rdb
6. Start Redis master first and then start the redis slaves
```bash
sudo service redis_6379 start
```

## Resque
#### Launch Resque(BG) Servers [Cloudformation Repo]
```bash
cd keas-infrastructure/ec2/dev/bg
sh run.sh
sh create.sh
```

## Memcached
#### Launch Memcached Server
```bash
cd keas-infrastructure/ec2/dev/memcached
sh run.sh
sh create.sh
```

#### Configure Memcached Server
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-memcached.yml -e "targethost=tag_Name_MemcachedDev1" -e "remoteuser=ubuntu" --private-key=~/.ssh/ec2deploykeasstagedev.key
```

## Frontend
#### Launch Frontend Servers [Cloudformation Repo]
```bash
cd keas-infrastructure/ec2/dev/frontend
sh run.sh
sh create.sh
```

#### Configure Frontend and BG Servers [Ansible Repo]
**Krubyapp Web**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-krubyapp.yml -e "targethost=tag_Name_frontenddevweb1" -e "remoteuser=ubuntu" -e "rubyversion=1.9.3-p551" -e "application_env=dev" -e "application_name=frontend" -e "bundler_version=1.11.2" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=~/.ssh/vault.password --tags web
```

**Krubyapp BG**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-krubyapp.yml -e "targethost=tag_Name_frontenddevbg1" -e "remoteuser=ubuntu" -e "rubyversion=1.9.3-p551" -e "application_env=dev" -e "application_name=frontend" -e "bundler_version=1.11.2" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=~/.ssh/vault.password --tags resque
```

**Krubyapp Index**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-krubyapp.yml -e "targethost=tag_Name_frontenddevindex1" -e "remoteuser=ubuntu" -e "rubyversion=1.9.3-p551" -e "application_env=dev" -e "application_name=frontend" -e "bundler_version=1.11.2" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=~/.ssh/vault.password --tags sphinx
```

**Frontend Web**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-frontend-app.yml  -e "targethost=tag_Name_frontenddevweb1" -e "remoteuser=ubuntu" -e "application_env=dev" -e "worker_count=6" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=~/.ssh/vault.password --tags web
```

**Frontend BG**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-frontend-app.yml -e "targethost=tag_Name_frontenddevbg1" -e "remoteuser=ubuntu" -e "application_env=dev" -e "worker_count=6" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=~/.ssh/vault.password --tags resque
```

**Frontend Index**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-frontend-app.yml -e "targethost=tag_Name_frontenddevindex1" -e "remoteuser=ubuntu" -e "application_env=dev" -e "worker_count=6" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=~/.ssh/vault.password --tags sphinx
```

## Biorecs
#### Launch Biorecs Servers [Cloudformation Repo]
```bash
cd keas-infrastructure/ec2/dev/biorecs
sh run.sh
sh create.sh
```

#### Configure Biorecs Servers [Ansible Repo]
**Biorecs Web**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-biorecs-app.yml -e "targethost=tag_Name_biorecsdev1" -e "remoteuser=ubuntu" -e "application_env=dev" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=~/.ssh/vault.password --tags web
```

**Biorecs BG**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-biorecs-app.yml -e "targethost=tag_Name_biorecsdev1" -e "remoteuser=ubuntu" -e "application_env=dev" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=~/.ssh/vault.password --tags resque
```

## Nest
#### Launch Nest Servers [Cloudformation Repo]
```bash
cd keas-infrastructure/ec2/dev/nest
sh run.sh
sh create.sh
```

#### Configure Nest Servers [Ansible Repo]
**Nest Web**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-nest-app.yml -e "targethost=tag_Name_nestdev1" -e "remoteuser=ubuntu" -e "app_env=dev" -e "listeners_count=2" -e "worker_count=2" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=~/.ssh/vault.password --tags web
```

**Nest BG**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-nest-app.yml -e "targethost=tag_Name_nestdevbg1" -e "remoteuser=ubuntu" -e "app_env=dev" -e "listeners_count=2" -e "worker_count=2" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=~/.ssh/vault.password --tags resque
```

## Ratatoskr
##### Launch Ratatoskr Servers [Cloudformation Repo]

```bash
cd keas-infrastructure/ec2/dev/ratatoskr
sh run.sh
sh create.sh
```

#### Configure Ratatoskr Server [Ansible Repo]
**Ratatoskr Web**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-ratatoskr-app.yml -e "targethost=tag_Name_ratatoskrdev1" -e "remoteuser=ubuntu" -e "app_env=dev" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=~/.ssh/vault.password --tags web
```

**Ratatoskr BG**
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-keas-ratatoskr-app.yml -e "targethost=tag_Name_ratatoskrdev1" -e "remoteuser=ubuntu" -e "app_env=dev" -e "listeners_count=2" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=~/.ssh/vault.password --tags resque
```

## Haproxy
#### Launch Haproxy Server [Cloudformation Repo]
Only Frontend and Nest needs to be accessed from outside world therefore we use 2 haproxy servers residing on public subnet which redirects the traffic from public to the corresponding application servers within the private subnet.
```bash
cd keas-infrastructure/ec2/dev/haproxy
sh run.sh
sh create.sh
```

#### Configure Haproxy Server [Ansible Repo]
**For Frontend haproxy**  
Edit the file [vars/frontend-dev.yml](https://github.com/welltok/ansible-playbooks/blob/keas-aws-migration/keas-infrastructure/roles/haproxy/vars/frontend-dev.yml) with the frontend web server private ip range and the domain name. Then run the below command

```bash
time ansible-playbook -i /etc/ansible/ec2.py role-haproxy.yml  -e "targethost=tag_Name_haproxyfrontenddev" -e "remoteuser=ubuntu" -e "application_name=frontend" -e "environment=dev" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=~/.ssh/vault.password
```

**For Nest haproxy**  
Edit the file [vars/nest-dev.yml](https://github.com/welltok/ansible-playbooks/blob/keas-aws-migration/keas-infrastructure/roles/haproxy/vars/nest-dev.yml) with the nest web server private ip range and the domain name. Then run the below command

```bash
time ansible-playbook -i /etc/ansible/ec2.py role-haproxy.yml  -e "targethost=tag_Name_haproxynestdev" -e "remoteuser=ubuntu" -e "application_name=nest" -e "environment=dev" --private-key=~/.ssh/ec2deploykeasstagedev.key --vault-password-file=~/.ssh/vault.password
```

The purpose of these haproxy servers are to route traffic from internet to private nginx web servers with SSL. <br>
Traffic Flows like: <br>
(Public Browser)-----> https://play-dev.keas.com(Public Subnet) ----> https://frontendweb1-dev.keas.com(Private Subnet)

**Elastic IP**
- From the AWS web console we need to create two Elastic IPs <br>
- *34.199.175.94* Associated with play-dev.keas.com <br>
- *52.4.196.190* Associated with nest-dev.keas.com <br>

The Public DNS server is currently maintained by George Feil. Any entries that needs to be added to the public should be updated to George so that he can add it to the DNS server. <br>

## Nagios
#### Launch nagios server [Cloudformation Repo]
```bash
cd keas-infrastructure/ec2/stage/nagios
sh run.sh
sh create.sh
```

#### Configure nagios server [Ansible Repo]
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-ansible-nagios.yml -e "targethost=tag_Name_nagiosstagedev" -e "remoteuser=ubuntu" --private-key=/home/vagrant/.ssh/ec2deploystagedev.pem -e "nagiospassword=admin" -e "nagiosservername=nagios-stagedev.keas.com" --vault-password-file=~/.ssh/vault.password
```
This brings up the web-console: https://nagios-stagedev.keas.com/nagios <br>
Username: nagiosadmin, Password: <given in the above ansible command>

#### Nagios Client [Ansible Repo]
Fetch the Private IP of Nagios server using the aws cli given below:
```bash
aws ec2 --region us-east-1 describe-instances --filters Name=tag-value,Values=nagiosstagedev --query 'Reservations[].Instances[].[Tags[?Key==`Name`].Value | [0], InstanceId, Placement.AvailabilityZone, InstanceType, State.Name, PrivateIpAddress]' --output table
```
Sample output
```bash
-----------------------------------------------------------------------------------------------
|                                      DescribeInstances                                      |
+----------------+----------------------+-------------+-----------+-----------+---------------+
|  nagiosstagedev|  i-09822935b356f408e |  us-east-1b |  t2.micro |  running  |  10.57.0.229  |
+----------------+----------------------+-------------+-----------+-----------+---------------+
```
Use the Nagios server IP from above to configure all the machines in dev environment.
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-nrpe.yml -e "targethost=tag_Env_dev" -e "remoteuser=ubuntu" -e "nagiosserverip=10.57.0.229" --private-key=~/.ssh/ec2deploystagedev.pem
```
- This command installs the nrpe client on the remote host machines and defines the nagios server for sending server stats.<br>

#### Configure nagios server with the recently added client
1. Edit roles/ansible-nagios-config/vars/dev.yml and add new host details <br>
example:
```bash
nagios_hosts:
  - {name: 'memcached1-dev', address: '10.57.0.113', groups: 'linux'}
```
2. Run role-ansible-nagios-config.yml play to add the new remote host to the nagios server.
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-ansible-nagios-config.yml -e "targethost=tag_Name_nagiosstagedev" -e "remoteuser=ubuntu" -e env="dev" --private-key=/home/vagrant/.ssh/ec2deploystagedev.pem
```
3. On successful run we should be able to view the new host monitor under nagios->services

## Splunk
#### Configure splunk for frontend web [Ansible Repo]
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-splunkforwarder.yml -e "targethost=tag_Name_frontenddevweb1" -e "remoteuser=ubuntu" -e "application_name=frontendweb" --private-key=/home/vagrant/.ssh/ec2deploystagedev.pem --vault-password-file=~/.ssh/vault.password
```
#### Configure splunk for frontend bg [Ansible Repo]
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-splunkforwarder.yml -e "targethost=tag_Name_frontenddevbg1" -e "remoteuser=ubuntu" -e "application_name=frontendbg" --private-key=/home/vagrant/.ssh/ec2deploystagedev.pem --vault-password-file=~/.ssh/vault.password
```
#### Configure splunk for frontend index [Ansible Repo]
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-splunkforwarder.yml -e "targethost=tag_Name_frontenddevindex1" -e "remoteuser=ubuntu" -e "application_name=frontendindex" --private-key=/home/vagrant/.ssh/ec2deploystagedev.pem --vault-password-file=~/.ssh/vault.password
```
#### Configure splunk for biorecs web [Ansible Repo]
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-splunkforwarder.yml -e "targethost=tag_Name_biorecsdev1" -e "remoteuser=ubuntu" -e "application_name=biorecsweb" --private-key=/home/vagrant/.ssh/ec2deploystagedev.pem --vault-password-file=~/.ssh/vault.password
```
#### Configure splunk for biorecs bg [Ansible Repo]
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-splunkforwarder.yml -e "targethost=tag_Name_biorecsdev1" -e "remoteuser=ubuntu" -e "application_name=biorecsbg" --private-key=/home/vagrant/.ssh/ec2deploystagedev.pem --vault-password-file=~/.ssh/vault.password
```
#### Configure splunk for Nest web [Ansible Repo]
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-splunkforwarder.yml -e "targethost=tag_Name_nestdev1" -e "remoteuser=ubuntu" -e "application_name=nestweb" --private-key=/home/vagrant/.ssh/ec2deploystagedev.pem --vault-password-file=~/.ssh/vault.password
```
#### Configure splunk for Nest bg [Ansible Repo]
```bash
time ansible-playbook -i /etc/ansible/ec2.py role-splunkforwarder.yml -e "targethost=tag_Name_nestdevbg1" -e "remoteuser=ubuntu" -e "application_name=nestbg" --private-key=/home/vagrant/.ssh/ec2deploystagedev.pem --vault-password-file=~/.ssh/vault.password
```

## Connection Details

#### Web Access

|App| Web URL|
|----|-------|
|Keas| https://play-dev.keas.com|
|Keas+| https://nest-dev.keas.com|
|Biorecs| https://biorecs1-dev.keas.com (only with VPN)|
|RabbitMQ Web| https://rabbitmq1-dev.keas.com (only with VPN)|
|Nagios Server|https://nagios-stagedev.keas.com/nagios (only with VPN)|

#### Database Hosts
|DB Engine|Host|Databases|
|---------|----|---|
|MySQL |stagedev-mysql.ct1vjcyxovqq.us-east-1.rds.amazonaws.com|frontend_dev,biorecs_dev,<br>frontend_stage,biorecs_stage|
|PostgreSQL|ratatoskr-dev-postgresql.ct1vjcyxovqq.us-east-1.rds.amazonaws.com|ratatoskr_dev1|

Sample commands to connect from CLI:
```bash
mysql -h stagedev-mysql.ct1vjcyxovqq.us-east-1.rds.amazonaws.com -u frontend_dev -p --ssl-ca=/etc/rds/ssl/rds-combined-ca-bundle.pem frontend_dev
mysql -h stagedev-mysql.ct1vjcyxovqq.us-east-1.rds.amazonaws.com -u biorecs_dev -p --ssl-ca=/etc/rds/ssl/rds-combined-ca-bundle.pem biorecs_dev
psql -h ratatoskr-dev-postgresql.ct1vjcyxovqq.us-east-1.rds.amazonaws.com -U ratatoskr -W ratatoskr_dev
```
SSL CA Cert is available for download from [AWS rds-combined-ca-bundle.pem](https://s3.amazonaws.com/rds-downloads/rds-combined-ca-bundle.pem)

## VPN Setup
[How to Setup the VPN using Viscosity Client](https://welltok.atlassian.net/wiki/display/PO/VPN+Instructions#VPNInstructions-HowtoaccessDev/StageBiorecsApplication:-)

